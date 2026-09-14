package com.jobradleasing.contractmanagement.shared.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A rate or quota, scale-normalised to 6 decimal places.
 *
 * SDD: See `documentation/modelling.definition.md`, "Money and percentages get types".
 */
@ConsistentCopyVisibility
data class Percentage private constructor(
    val value: BigDecimal,
) {
    companion object {
        private const val SCALE = 6

        fun of(value: String): Percentage = Percentage(BigDecimal(value).setScale(SCALE, RoundingMode.HALF_UP))

        fun of(value: BigDecimal): Percentage = Percentage(value.setScale(SCALE, RoundingMode.HALF_UP))
    }
}
