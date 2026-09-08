package com.example.contractmanagement.mlc.inbound.graphql

/**
 * GraphQL payload for UC07 — CreateMasterLeasingContract.
 *
 * Carries § 3's four derived values. Because no source specifies a response shape and no
 * domain test can falsify the derivation — this service has no read side — `uc07` § 3 makes
 * the executable request in `api/uc07-create-master-leasing-contract.graphql` accountable for
 * this shape instead. If the derivation is wrong, that file is where it shows.
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 3, § 9.
 */
data class CreateMasterLeasingContractPayload(
    val masterLeasingContractId: String,
    val status: String,
    val configurationId: String,
    val configurationVersion: Int,
)
