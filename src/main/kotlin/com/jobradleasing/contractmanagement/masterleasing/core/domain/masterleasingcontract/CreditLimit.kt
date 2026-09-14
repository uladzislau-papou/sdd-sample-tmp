package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Money
import java.math.BigDecimal

/**
 * The maximum credit exposure permitted on this contract.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class CreditLimit(
    val value: Money,
) {
    init {
        if (value.amount < BigDecimal.ZERO) {
            throw InvalidMasterLeasingContractException("creditLimitAmount must be non-negative: ${value.amount}")
        }
    }
}
