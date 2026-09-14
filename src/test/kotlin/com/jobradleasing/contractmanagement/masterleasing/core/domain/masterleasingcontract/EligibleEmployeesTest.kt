package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EligibleEmployeesTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenNegative() {
        assertThatThrownBy { EligibleEmployees(-1) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
