package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [EligibleEmployees] [*JobRad-Berechtigte*].
 *
 * The `> 0` rule is **PD-10**. A contract entitling nobody to lease is the case it rejects.
 */
class EligibleEmployeesTest {
    @Test
    fun zeroThrows() {
        assertThatThrownBy { EligibleEmployees(0) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("eligibleEmployees")
    }
}
