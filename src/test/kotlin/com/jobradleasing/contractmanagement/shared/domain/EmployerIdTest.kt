package com.jobradleasing.contractmanagement.shared.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EmployerIdTest {
    @Test
    fun throwsIllegalArgumentException_whenBlank() {
        assertThatThrownBy { EmployerId("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
