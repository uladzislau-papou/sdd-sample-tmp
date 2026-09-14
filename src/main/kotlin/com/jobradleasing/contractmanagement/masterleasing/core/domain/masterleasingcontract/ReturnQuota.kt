package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Percentage
import java.math.BigDecimal

/**
 * *Rückgabekontingent* — the share of active leases per year that may be
 * dissolved without penalty.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class ReturnQuota(
    val value: Percentage,
) {
    companion object {
        private val MAX = BigDecimal(100)
    }

    init {
        if (value.value < BigDecimal.ZERO || value.value > MAX) {
            throw InvalidMasterLeasingContractException(
                "returnQuotaPercentage must be between 0 and 100: ${value.value}",
            )
        }
    }
}
