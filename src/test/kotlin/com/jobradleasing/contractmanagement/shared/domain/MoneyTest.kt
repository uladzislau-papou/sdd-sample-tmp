package com.jobradleasing.contractmanagement.shared.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class MoneyTest {
    @Test
    fun compareTo_throwsIllegalArgumentException_whenCurrenciesDiffer() {
        val eur = Money.of(BigDecimal("100.0000"), Currency.getInstance("EUR"))
        val chf = Money.of(BigDecimal("100.0000"), Currency.getInstance("CHF"))

        assertThatThrownBy { eur > chf }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
