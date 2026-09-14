package com.jobradleasing.contractmanagement.shared.domain

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class LessorIdTest {
    @Test
    fun throwsIllegalArgumentException_whenBlank() {
        assertThatThrownBy { LessorId("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
