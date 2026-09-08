package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [CurrencyCode].
 *
 * The source gives only the German term *Währung* and no format, so the three-uppercase-letter
 * shape is **PD-10**. It is checked as a shape rather than against a currency table: a table
 * would be a data dependency this service has no source for, and an unknown-but-well-formed
 * code is a business question, not a malformed input.
 */
class CurrencyCodeTest {
    @Test
    fun lowercaseThrows() {
        assertThatThrownBy { CurrencyCode("eur") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("currency")
    }

    @Test
    fun wrongLengthThrows() {
        assertThatThrownBy { CurrencyCode("EURO") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun acceptsAThreeLetterUppercaseCode() {
        assertThat(CurrencyCode("EUR").value).isEqualTo("EUR")
    }
}
