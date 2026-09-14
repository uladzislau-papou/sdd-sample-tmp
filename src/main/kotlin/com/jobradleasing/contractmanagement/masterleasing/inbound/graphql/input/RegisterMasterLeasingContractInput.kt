package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql.input

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.ContractType
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.InheritanceMode
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.SalesChannel

/**
 * Inbound GraphQL argument type for `registerMasterLeasingContract`.
 *
 * SDD: See `documentation/use-cases/uc01-register-master-leasing-contract.spec.md` § 9.
 */
data class RegisterMasterLeasingContractInput(
    val employerId: String,
    val lessorId: String,
    val partnerNumber: String,
    /** Null for a standalone contract; the base contract's id for an affiliated one. */
    val parentMasterLeasingContractId: String?,
    val configuration: MlcConfigurationInput,
)

data class MlcConfigurationInput(
    val contractType: ContractType,
    val salesChannel: SalesChannel,
    val inheritanceMode: InheritanceMode,
    val currency: String,
    val creditLimitAmount: String,
    val priceRangeMin: String,
    val priceRangeMax: String,
    val eligibleEmployees: Int,
    val jointLiability: Boolean,
    val returnQuotaPercentage: String,
    val noticePeriodMonths: Int,
)
