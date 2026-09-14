package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CancellationReasonTest {
    @Test
    fun throwsInvalidMasterLeasingContractException_whenBlank() {
        assertThatThrownBy { CancellationReason("   ") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun throwsInvalidMasterLeasingContractException_whenOverMaxLength() {
        assertThatThrownBy { CancellationReason("a".repeat(401)) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
