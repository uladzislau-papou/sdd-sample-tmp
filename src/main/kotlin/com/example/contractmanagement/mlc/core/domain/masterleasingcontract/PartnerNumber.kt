package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * The join key between Odoo and Radar for this contract's partner [*Partnernummer*].
 *
 * External identity, local type — `adr/0017`, `adr/0023`.
 *
 * **Optional, and the absence has a documented meaning** as
 * `coding-style.definition.md` § 1.4 requires: null means the caller does not yet hold the
 * number. **PD-03** decides that we accept it when the caller has it and never fetch it —
 * fetching would add the first outbound call to a foreign system and entangle this use case
 * with the MVP page's unresolved synchronisation-strategy risk.
 *
 * So the invariant is *non-blank when present*. A blank string is a malformed value, not an
 * absent one; absence is the nullable type at the call site.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
@ConsistentCopyVisibility
data class PartnerNumber private constructor(
    val value: String,
) {
    companion object {
        operator fun invoke(value: String): PartnerNumber {
            if (value.isBlank()) {
                throw InvalidMasterLeasingContractException(
                    "partnerNumber must not be blank when present; use null for absent",
                )
            }
            return PartnerNumber(value)
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
        fun reconstitute(value: String): PartnerNumber = PartnerNumber(value)
    }
}
