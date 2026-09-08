package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import java.math.BigDecimal

/**
 * The band of bike prices a contract's conditions permit leasing.
 *
 * **One type holding both bounds**, because the rule that matters is a *relationship* between
 * them — `min <= max` — and a relationship has nowhere to live if each bound is a field of its
 * own. That is also what makes a half-populated range unrepresentable: the whole range is
 * optional (**PD-01**), so absence is a null `PriceRange`, never a `min` without a `max`.
 *
 * All three rules — both bounds non-negative, `min <= max` — are **PD-10**. AC-07 asserts the
 * ordering rule at the use-case level, which is the one place a caller can observe it.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class PriceRange private constructor(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    companion object {
        operator fun invoke(
            min: BigDecimal,
            max: BigDecimal,
        ): PriceRange {
            if (min < BigDecimal.ZERO || max < BigDecimal.ZERO) {
                throw InvalidMasterLeasingContractException(
                    "priceRangeMin and priceRangeMax must not be negative, were: $min and $max",
                )
            }
            if (min > max) {
                throw InvalidMasterLeasingContractException(
                    "priceRangeMin must not exceed priceRangeMax, were: $min and $max",
                )
            }
            return PriceRange(min, max)
        }

        /**
         * Builds a range from two optional bounds, enforcing that they are supplied
         * **together or not at all**.
         *
         * This is the fourth rule in `uc07` § 2.3, and the one the implementation exposed
         * rather than the specification anticipating it: a command carries two nullable bounds
         * while the domain carries one nullable range, so the mapping has four input
         * combinations and § 2.3 originally named a rule for only two of them. A single bound
         * describes no band of prices.
         *
         * It lives here and **not** in the driver because it is a domain rule, and
         * `architecture.definition.md` § 4.4 leaves a driver no rules of its own — calling this
         * is a delegation, which is all a driver is permitted to do.
         *
         * @return null when both bounds are absent — the optional-range case (**PD-01**)
         * @throws InvalidMasterLeasingContractException if exactly one bound is present, or if
         *   the pair violates any invariant above
         */
        fun of(
            min: BigDecimal?,
            max: BigDecimal?,
        ): PriceRange? =
            when {
                min == null && max == null -> null
                min == null || max == null ->
                    throw InvalidMasterLeasingContractException(
                        "priceRangeMin and priceRangeMax must be supplied together or not at all, " +
                            "were: $min and $max",
                    )
                // `invoke(...)` and not `PriceRange(...)`: inside the companion the latter
                // resolves to the **private constructor** and silently skips validation. That
                // regression shipped for one compile and was caught by
                // `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenPriceRangeMinExceedsMax`.
                else -> invoke(min, max)
            }

        /**
         * Rebuilds from a persisted value **without validating**.
         *
         * See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 4: the rules
         * above are **PD-10** — provisional, and expected to be replaced by the validation
         * matrix the JCM project has not built yet. Re-validating on read would let a tightened
         * rule make an already-signed contract unloadable, which is the one failure this domain
         * cannot accept.
         *
         * `ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence` restricts the caller to
         * `..outbound.persistence..`, matching any `reconstitute` in `core.domain`.
         */
        fun reconstitute(
            min: BigDecimal,
            max: BigDecimal,
        ): PriceRange = PriceRange(min, max)
    }
}
