package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PartnerNumberTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenBlank() {
        assertThatThrownBy { PartnerNumber("   ") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
