package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NoticePeriodTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenZeroMonths() {
        assertThatThrownBy { NoticePeriod(0) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun acceptsOneMonth() {
        assertThat(NoticePeriod(1).value).isEqualTo(1)
    }

    @Test
    fun acceptsThirtySixMonths() {
        assertThat(NoticePeriod(36).value).isEqualTo(36)
    }

    @Test
    fun throwsInvalidMasterLeasingContractException_whenThirtySevenMonths() {
        assertThatThrownBy { NoticePeriod(37) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
