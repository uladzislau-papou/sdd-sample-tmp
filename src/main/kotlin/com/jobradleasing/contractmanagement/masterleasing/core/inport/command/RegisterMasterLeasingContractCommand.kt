package com.jobradleasing.contractmanagement.masterleasing.core.inport.command

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.ContractType
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.InheritanceMode
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.SalesChannel
import java.math.BigDecimal

/**
 * Input to `RegisterMasterLeasingContractUseCase`.
 *
 * SDD: See `documentation/ports/register-master-leasing-contract.inport.spec.md` § 2.1.
 */
data class RegisterMasterLeasingContractCommand(
    val employerId: String,
    val lessorId: String,
    val partnerNumber: String,
    /** Null for a standalone contract; the base contract's id for an affiliated one. */
    val parentMasterLeasingContractId: String?,
    val configuration: MlcConfigurationCommand,
)

data class MlcConfigurationCommand(
    val contractType: ContractType,
    val salesChannel: SalesChannel,
    val inheritanceMode: InheritanceMode,
    val currency: String,
    val creditLimitAmount: BigDecimal,
    val priceRangeMin: BigDecimal,
    val priceRangeMax: BigDecimal,
    val eligibleEmployees: Int,
    val jointLiability: Boolean,
    val returnQuotaPercentage: BigDecimal,
    val noticePeriodMonths: Int,
)
