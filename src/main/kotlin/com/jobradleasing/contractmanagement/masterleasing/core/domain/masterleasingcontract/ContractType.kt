package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

/**
 * The commercial model the contract runs under.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
enum class ContractType {
    SALARY_SACRIFICE,
    EMPLOYER_FUNDED,
    MIXED,
}
