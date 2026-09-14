package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.shared.domain.Money
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CreditLimitTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenNegative() {
        assertThatThrownBy { CreditLimit(Money.euro("-1.0000")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
