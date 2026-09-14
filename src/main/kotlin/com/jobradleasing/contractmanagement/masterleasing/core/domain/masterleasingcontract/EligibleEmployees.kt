package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * *JobRad-Berechtigte* — the number of employees eligible under this contract.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class EligibleEmployees(
    val value: Int,
) {
    init {
        if (value < 0) {
            throw InvalidMasterLeasingContractException("eligibleEmployees must be non-negative: $value")
        }
    }
}
