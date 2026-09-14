package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql.payload

/**
 * Outbound GraphQL result type for `registerMasterLeasingContract`.
 *
 * SDD: See `documentation/use-cases/uc01-register-master-leasing-contract.spec.md` § 9.
 */
data class RegisterMasterLeasingContractPayload(
    val masterLeasingContractId: String,
    val status: String,
    val configurationVersion: Int,
)
