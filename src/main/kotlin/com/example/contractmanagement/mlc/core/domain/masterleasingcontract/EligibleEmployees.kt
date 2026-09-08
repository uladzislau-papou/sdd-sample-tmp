package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * How many of an employer's staff are entitled to lease under a contract
 * [*JobRad-Berechtigte*].
 *
 * The `> 0` rule is **PD-10**: a framework agreement entitling nobody is the case it rejects.
 * No upper bound, because any bound we chose would be invented and the real one belongs in
 * the validation matrix that does not exist yet.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class EligibleEmployees private constructor(
    val value: Int,
) {
    companion object {
        operator fun invoke(value: Int): EligibleEmployees {
            if (value < 1) {
                throw InvalidMasterLeasingContractException(
                    "eligibleEmployees must be greater than 0, was: $value",
                )
            }
            return EligibleEmployees(value)
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
        fun reconstitute(value: Int): EligibleEmployees = EligibleEmployees(value)
    }
}
