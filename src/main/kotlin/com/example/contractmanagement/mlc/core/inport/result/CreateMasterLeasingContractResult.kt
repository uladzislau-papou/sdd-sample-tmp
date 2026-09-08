package com.example.contractmanagement.mlc.core.inport.result

/**
 * Output of the CreateMasterLeasingContract use case (UC07).
 *
 * **Derived, not sourced.** No source specifies a response shape. `uc07` § 3 derives these
 * four values from what the caller cannot otherwise learn: this service has no read side, so a
 * caller that is not told the identifiers has no second way to obtain them.
 *
 * The configuration's identity and version are here because the source defines `mlc_config_id`
 * as "the current configuration version" — a later terms version needs the previous one to be
 * nameable.
 *
 * Because the derivation is unfalsifiable by a domain test, the shape is held accountable by
 * the executable request in `api/uc07-create-master-leasing-contract.graphql` instead. If the
 * derivation is wrong, that file is where it shows.
 *
 * SDD: see `documentation/ports/create-master-leasing-contract.inport.spec.md` § 2.2.
 */
data class CreateMasterLeasingContractResult(
    val masterLeasingContractId: String,
    val status: String,
    val configurationId: String,
    val configurationVersion: Int,
)
