package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

/**
 * The versioned commercial terms of a `MasterLeasingContract`. A domain entity
 * inside the aggregate, not an aggregate of its own — see
 * `documentation/domain/aggregate-master-leasing-contract.spec.md` § 1.
 */
data class MlcConfiguration(
    val version: ConfigurationVersion,
    val inheritanceMode: InheritanceMode,
    val contractType: ContractType,
    val salesChannel: SalesChannel,
    val creditLimit: CreditLimit,
    val priceRange: PriceRange,
    val eligibleEmployees: EligibleEmployees,
    val jointLiability: Boolean,
    val returnQuota: ReturnQuota,
    val noticePeriod: NoticePeriod,
)
