package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import java.math.BigDecimal

/**
 * The maximum credit exposure allowed on a contract.
 *
 * The `> 0` rule is **PD-10** — ours, not sourced. The MVP page's risk 1 records that the
 * linkage rules are not modelled anywhere yet and that the measure against it is to *build
 * the matrix*; the matrix does not exist. So this rejects only a value that could not be
 * meaningful under any matrix, and encodes no threshold the business might set differently.
 *
 * `BigDecimal` and not `Double`: this is money, and binary floating point cannot represent
 * it exactly.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class CreditLimit private constructor(
    val value: BigDecimal,
) {
    companion object {
        operator fun invoke(value: BigDecimal): CreditLimit {
            if (value <= BigDecimal.ZERO) {
                throw InvalidMasterLeasingContractException(
                    "creditLimit must be greater than 0, was: $value",
                )
            }
            return CreditLimit(value)
        }

        /**
         * Rebuilds from a persisted value **without validating**.
         *
         * See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 4: the rule
         * above is **PD-01** or **PD-10** — provisional, and expected to be replaced by the
         * validation matrix the JCM project has not built yet. Re-validating on read would let
         * a tightened rule make an already-signed contract unloadable, which is the one failure
         * this domain cannot accept.
         *
         * `ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence` restricts the caller to
         * `..outbound.persistence..`. It matches any `reconstitute` in `core.domain`, so this
         * factory is guarded by the same rule the aggregates are — the enforcement did not have
         * to be added.
         */
        fun reconstitute(value: BigDecimal): CreditLimit = CreditLimit(value)
    }
}
