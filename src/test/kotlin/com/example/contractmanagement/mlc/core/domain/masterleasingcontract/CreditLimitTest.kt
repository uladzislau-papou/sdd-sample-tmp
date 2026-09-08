package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Domain tests for [CreditLimit].
 *
 * The `> 0` rule is **PD-10**, and PD-10 is the weakest-footed decision on the spec: the real
 * validation matrix does not exist (MVP risk 1 says so). This rule is of the narrow kind
 * PD-10 permits — it rejects a value that could not be meaningful under any matrix, and
 * encodes no threshold the business might set differently.
 */
class CreditLimitTest {
    @Test
    fun zeroThrows() {
        assertThatThrownBy { CreditLimit(BigDecimal.ZERO) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("creditLimit")
    }

    @Test
    fun negativeThrows() {
        assertThatThrownBy { CreditLimit(BigDecimal("-1")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
