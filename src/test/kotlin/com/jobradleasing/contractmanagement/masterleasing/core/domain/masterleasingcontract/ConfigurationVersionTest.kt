package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConfigurationVersionTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenBelowOne() {
        assertThatThrownBy { ConfigurationVersion(0) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
