package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Percentage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReturnQuotaTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenNegative() {
        assertThatThrownBy { ReturnQuota(Percentage.of("-1")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun throwsInvalidMasterLeasingContractException_whenAboveOneHundred() {
        assertThatThrownBy { ReturnQuota(Percentage.of("100.000001")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun acceptsOneHundred() {
        assertThat(ReturnQuota(Percentage.of("100")).value).isEqualTo(Percentage.of("100"))
    }
}
