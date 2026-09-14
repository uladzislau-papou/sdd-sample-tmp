package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Money
import java.math.BigDecimal

/**
 * The band of bike prices this contract's conditions permit leasing.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class PriceRange(
    val min: Money,
    val max: Money,
) {
    init {
        if (min.currency != max.currency) {
            throw InvalidMasterLeasingContractException(
                "priceRangeMin and priceRangeMax must share one currency: ${min.currency} vs ${max.currency}",
            )
        }
        if (min.amount < BigDecimal.ZERO || max.amount < BigDecimal.ZERO) {
            throw InvalidMasterLeasingContractException(
                "priceRangeMin and priceRangeMax must be non-negative: $min, $max",
            )
        }
        if (min > max) {
            throw InvalidMasterLeasingContractException("priceRangeMin ($min) must not exceed priceRangeMax ($max)")
        }
    }
}
