package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [PartnerNumber].
 *
 * Optional at creation (**PD-03**: accepted when the caller holds it, never fetched), so the
 * rule under test is *non-blank when present* — absence is expressed by a nullable type at
 * the call site, not by a blank string here (`coding-style.definition.md` § 1.4).
 */
class PartnerNumberTest {
    @Test
    fun blankThrows() {
        assertThatThrownBy { PartnerNumber("  ") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("partnerNumber")
    }
}
