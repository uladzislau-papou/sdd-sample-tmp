package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Money
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

class PriceRangeTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenCurrenciesDiffer() {
        val min = Money.of(BigDecimal("500.0000"), Currency.getInstance("EUR"))
        val max = Money.of(BigDecimal("3000.0000"), Currency.getInstance("CHF"))

        assertThatThrownBy { PriceRange(min, max) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun throwsInvalidMasterLeasingContractException_whenMinIsNegative() {
        val min = Money.euro("-500.0000")
        val max = Money.euro("3000.0000")

        assertThatThrownBy { PriceRange(min, max) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun throwsInvalidMasterLeasingContractException_whenMinExceedsMax() {
        val min = Money.euro("3000.0000")
        val max = Money.euro("1000.0000")

        assertThatThrownBy { PriceRange(min, max) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
