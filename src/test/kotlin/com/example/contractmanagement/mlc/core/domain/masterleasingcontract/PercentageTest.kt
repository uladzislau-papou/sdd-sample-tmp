package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Domain tests for [Percentage].
 *
 * Shared by `returnQuotaPercentage` [*Rückgabekontingent*] and `earlyClaimFeePercentage`,
 * which is why it is one type rather than two: the `0..100` rule is **PD-10** and identical
 * for both, and two types would give the same invented rule two places to drift.
 *
 * Both bounds are inclusive, asserted explicitly — an off-by-one at a boundary is the failure
 * this type exists to prevent and the one a range check gets wrong.
 */
class PercentageTest {
    @Test
    fun aboveHundredThrows() {
        assertThatThrownBy { Percentage(BigDecimal("100.01")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun negativeThrows() {
        assertThatThrownBy { Percentage(BigDecimal("-0.01")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun acceptsBothBounds() {
        assertThat(Percentage(BigDecimal.ZERO).value).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(Percentage(BigDecimal("100")).value).isEqualByComparingTo(BigDecimal("100"))
    }
}
