package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import java.math.BigDecimal

/**
 * A percentage between 0 and 100 inclusive.
 *
 * Shared by `returnQuotaPercentage` [*Rückgabekontingent*] and `earlyClaimFeePercentage`.
 * **One type rather than two**, because the `0..100` rule is **PD-10** and identical for both
 * — and an invented rule with two homes is an invented rule that will drift.
 *
 * Both bounds are inclusive: a return quota of 100% and a fee of 0% are both meaningful, so
 * excluding them would reject valid terms.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class Percentage private constructor(
    val value: BigDecimal,
) {
    companion object {
        private val HUNDRED: BigDecimal = BigDecimal("100")

        operator fun invoke(value: BigDecimal): Percentage {
            if (value < BigDecimal.ZERO || value > HUNDRED) {
                throw InvalidMasterLeasingContractException(
                    "percentage must be between 0 and 100 inclusive, was: $value",
                )
            }
            return Percentage(value)
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
        fun reconstitute(value: BigDecimal): Percentage = Percentage(value)
    }
}
