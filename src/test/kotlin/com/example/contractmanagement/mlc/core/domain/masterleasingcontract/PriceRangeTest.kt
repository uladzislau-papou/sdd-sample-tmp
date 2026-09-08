package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Domain tests for [PriceRange].
 *
 * One type holding both bounds rather than two independent fields, because the rule that
 * matters is a *relationship* between them — `min <= max` — and a relationship has nowhere to
 * live if each bound is its own value. **PD-10**; AC-07 is the use-case-level assertion of the
 * same rule.
 *
 * The whole range is optional (**PD-01**), so absence is a null `PriceRange` and never a
 * half-populated one: `priceRangeMin` without `priceRangeMax` cannot be represented.
 */
class PriceRangeTest {
    @Test
    fun minAboveMaxThrows() {
        assertThatThrownBy { PriceRange(BigDecimal("900"), BigDecimal("800")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("priceRangeMin")
    }

    @Test
    fun negativeBoundThrows() {
        assertThatThrownBy { PriceRange(BigDecimal("-1"), BigDecimal("800")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    /**
     * [PriceRange.of] is where the **pairing** rule lives — the fourth row of § 2.3 and the one
     * the implementation exposed rather than the blocked revision anticipating it. The command
     * carries two nullable bounds, the domain carries one nullable range, so the mapping has
     * four combinations and only two had a rule.
     *
     * It is a domain factory and not driver logic on purpose: "one bound without the other is
     * invalid" is a domain rule, and `architecture.definition.md` § 4.4 leaves a driver no
     * rules of its own.
     */
    @Test
    fun of_throwsInvalidMasterLeasingContractException_whenOnlyOneBoundPresent() {
        assertThatThrownBy { PriceRange.of(BigDecimal("749"), null) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
            .hasMessageContaining("together")

        assertThatThrownBy { PriceRange.of(null, BigDecimal("11000")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }

    @Test
    fun of_returnsNull_whenBothBoundsAbsent() {
        assertThat(PriceRange.of(null, null)).isNull()
    }

    @Test
    fun of_buildsTheRange_whenBothBoundsPresent() {
        val range = PriceRange.of(BigDecimal("749"), BigDecimal("11000"))

        assertThat(range?.min).isEqualByComparingTo(BigDecimal("749"))
        assertThat(range?.max).isEqualByComparingTo(BigDecimal("11000"))
    }

    @Test
    fun acceptsEqualBounds() {
        val range = PriceRange(BigDecimal("800"), BigDecimal("800"))

        assertThat(range.min).isEqualByComparingTo(BigDecimal("800"))
        assertThat(range.max).isEqualByComparingTo(BigDecimal("800"))
    }
}
