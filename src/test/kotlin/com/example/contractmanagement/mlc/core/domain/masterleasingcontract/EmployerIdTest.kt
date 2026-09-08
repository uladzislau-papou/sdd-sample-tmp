package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Domain tests for [EmployerId].
 *
 * Its presence rule is the **one** rule in `uc07-create-master-leasing-contract.spec.md`
 * § 2.3 argued from the sources rather than decided by a provisional decision: the MVP page
 * defines this contract as the agreement established *with an employer*, so an instance
 * without one is not the thing being modelled. AC-03 rests on it.
 *
 * Local to `mlc` rather than shared, per `adr/0023`. It throws a domain exception because it
 * is constructed from a command (`coding-style.definition.md` § 6.2 clause one).
 */
class EmployerIdTest {
    @Test
    fun blankThrows() {
        assertThatThrownBy { EmployerId(" ") }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("employerId")
    }

    @Test
    fun keepsItsValue() {
        assertThat(EmployerId("emp-1").value).isEqualTo("emp-1")
    }
}
