package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * Reference to the employer a master leasing contract is established with [*Arbeitgeber*].
 *
 * **External identity, local type.** The employer is not ours (`adr/0017`), and
 * `adr/0023` decides that this stays a Value Object of the `mlc` context rather than joining
 * `shared.domain`: `adr/0005`'s category-3 test needs a second context referencing it, and
 * `ilc` does not exist yet.
 *
 * Opaque: no format is assumed beyond being non-blank, because the owning system's identifier
 * shape is not documented anywhere this service can read.
 *
 * Its presence rule is the one rule in `uc07` § 2.3 argued from the sources rather than
 * decided provisionally. AC-03 rests on it.
 *
 * Throws a domain exception rather than [IllegalArgumentException] because the value arrives
 * from a command, so this is the last guard guaranteed to run
 * (`coding-style.definition.md` § 6.2 clause one).
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class EmployerId private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): EmployerId {
            if (value.isBlank()) {
                throw InvalidMasterLeasingContractException("employerId must not be blank")
            }
            return EmployerId(value)
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
        fun reconstitute(value: String): EmployerId = EmployerId(value)
    }
}
