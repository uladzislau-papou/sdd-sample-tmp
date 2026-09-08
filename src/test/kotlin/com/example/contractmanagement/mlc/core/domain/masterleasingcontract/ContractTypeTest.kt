package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [ContractType].
 *
 * Required at creation by **PD-01**. The source describes it as "the commercial model the
 * contract runs under (e.g. salary-sacrifice leasing)" and enumerates no values, so the
 * invariant is presence only — constraining it to a set would invent a vocabulary.
 */
class ContractTypeTest {
    @Test
    fun blankThrows() {
        assertThatThrownBy { ContractType("") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("contractType")
    }
}
