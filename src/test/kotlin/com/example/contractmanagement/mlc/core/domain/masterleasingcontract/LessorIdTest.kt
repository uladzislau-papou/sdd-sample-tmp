package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [LessorId].
 *
 * Required at creation by **PD-01**, which is a decision of ours and not a sourced rule.
 * Local to `mlc` per `adr/0023`.
 */
class LessorIdTest {
    @Test
    fun blankThrows() {
        assertThatThrownBy { LessorId("") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("lessorId")
    }
}
