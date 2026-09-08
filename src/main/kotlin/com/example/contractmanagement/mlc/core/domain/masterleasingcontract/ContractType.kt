package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * The commercial model a contract runs under — for example salary-sacrifice leasing.
 *
 * Required at creation by **PD-01**. Presence is the only invariant: the source describes the
 * field and enumerates **no values**, so constraining it to a set would invent a vocabulary
 * the business never stated. It is a `String` and not an enum for the same reason — an enum
 * would make an unlisted model unrepresentable, which is a stronger claim than the source
 * supports.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class ContractType private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): ContractType {
            if (value.isBlank()) {
                throw InvalidMasterLeasingContractException("contractType must not be blank")
            }
            return ContractType(value)
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
        fun reconstitute(value: String): ContractType = ContractType(value)
    }
}
