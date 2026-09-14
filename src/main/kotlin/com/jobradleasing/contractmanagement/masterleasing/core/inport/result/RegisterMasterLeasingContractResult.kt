package com.jobradleasing.contractmanagement.masterleasing.core.inport.result

/**
 * Output of `RegisterMasterLeasingContractUseCase`.
 *
 * SDD: See `documentation/ports/register-master-leasing-contract.inport.spec.md` § 2.2.
 */
data class RegisterMasterLeasingContractResult(
    val masterLeasingContractId: String,
    val status: String,
    val configurationVersion: Int,
)
