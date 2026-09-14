package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * The version of an `MlcConfiguration`.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class ConfigurationVersion(
    val value: Int,
) {
    init {
        if (value < 1) {
            throw InvalidMasterLeasingContractException("configuration version must be >= 1: $value")
        }
    }
}
