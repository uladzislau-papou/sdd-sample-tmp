package com.jobradleasing.contractmanagement.shared.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * A monetary amount, scale-normalised to 4 decimal places.
 *
 * SDD: See `documentation/modelling.definition.md`, "Money and percentages get types".
 */
@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: Currency,
) {
    companion object {
        private const val SCALE = 4

        fun of(
            amount: BigDecimal,
            currency: Currency,
        ): Money = Money(amount.setScale(SCALE, RoundingMode.HALF_UP), currency)

        fun euro(amount: String): Money = of(BigDecimal(amount), Currency.getInstance("EUR"))
    }

    operator fun compareTo(other: Money): Int {
        require(currency == other.currency) {
            "Cannot compare Money in different currencies: $currency vs ${other.currency}"
        }
        return amount.compareTo(other.amount)
    }
}
