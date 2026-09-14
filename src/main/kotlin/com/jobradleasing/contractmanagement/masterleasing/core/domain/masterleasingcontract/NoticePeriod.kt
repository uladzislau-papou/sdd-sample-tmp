package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * *Kündigungsfrist* — the notice period, in months.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class NoticePeriod(
    val value: Int,
) {
    companion object {
        private const val MIN_MONTHS = 1
        private const val MAX_MONTHS = 36
    }

    init {
        if (value < MIN_MONTHS || value > MAX_MONTHS) {
            throw InvalidMasterLeasingContractException(
                "noticePeriodMonths must be between $MIN_MONTHS and $MAX_MONTHS: $value",
            )
        }
    }
}
