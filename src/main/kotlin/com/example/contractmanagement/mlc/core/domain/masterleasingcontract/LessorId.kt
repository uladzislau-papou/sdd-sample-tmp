package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * Reference to the lessor party of a master leasing contract [*Leasinggeber*].
 *
 * External identity, local type, for the same reasons as [EmployerId] — `adr/0017` and
 * `adr/0023`. Required at creation by **PD-01**, which is a decision of ours: no source
 * states field optionality.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class LessorId private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): LessorId {
            if (value.isBlank()) {
                throw InvalidMasterLeasingContractException("lessorId must not be blank")
            }
            return LessorId(value)
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
        fun reconstitute(value: String): LessorId = LessorId(value)
    }
}
