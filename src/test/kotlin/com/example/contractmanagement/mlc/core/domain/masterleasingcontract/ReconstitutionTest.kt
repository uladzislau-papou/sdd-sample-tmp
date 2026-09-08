package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * The read path accepts values the write path rejects.
 *
 * **This test exists because the property was claimed and not delivered.**
 * `documentation/domain/aggregate-master-leasing-contract.spec.md` § 4 and
 * `documentation/ports/master-leasing-contract-repository.outport.spec.md` § 4 both stated that
 * reconstitution re-checks no invariant. `MasterLeasingContract.reconstitute` and
 * `ContractConfiguration.reconstitute` genuinely re-check nothing — but they receive
 * *already-constructed* value objects, and the mapper was constructing them through validating
 * constructors. So the read path validated everything, and both specs said it did not.
 *
 * `ddd-hex-reviewer` found it. Nothing could have found it earlier:
 * `MasterLeasingContractJpaRepositoryIT` only round-trips values that satisfy the current
 * rules, so the claim was unfalsifiable in both directions. That is the gap this file closes —
 * every case below stores a value that **violates a current rule** and asserts the read path
 * accepts it anyway.
 *
 * **Why the property is worth this much machinery.** Every rule it bypasses is **PD-10** or
 * **PD-01** — a provisional decision of ours, taken because the JCM project has not answered
 * the question, and documented as expected to change. If the real validation matrix turns out
 * stricter in any dimension, a validating read path makes every already-signed contract
 * unloadable. That is not a migration inconvenience; it is an outage on legal records.
 *
 * The pairs below are deliberately the *most likely* tightenings, not arbitrary invalid values:
 * a currency table replacing a shape check, a percentage ceiling below 100, a minimum credit
 * limit above zero.
 */
class ReconstitutionTest {
    @Test
    @DisplayName("A stored currency that the current shape rule rejects still loads")
    fun reconstitute_acceptsACurrencyTheWritePathRejects() {
        assertThatThrownBy { CurrencyCode("eur") }.isInstanceOf(InvalidMasterLeasingContractException::class.java)

        assertThat(CurrencyCode.reconstitute("eur").value).isEqualTo("eur")
    }

    @Test
    @DisplayName("A stored percentage above the current ceiling still loads")
    fun reconstitute_acceptsAPercentageAboveTheCurrentCeiling() {
        assertThatThrownBy {
            Percentage(BigDecimal("120"))
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)

        assertThat(Percentage.reconstitute(BigDecimal("120")).value).isEqualByComparingTo(BigDecimal("120"))
    }

    @Test
    @DisplayName("A stored zero credit limit still loads")
    fun reconstitute_acceptsACreditLimitTheWritePathRejects() {
        assertThatThrownBy {
            CreditLimit(BigDecimal.ZERO)
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)

        assertThat(CreditLimit.reconstitute(BigDecimal.ZERO).value).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    @DisplayName("A stored inverted price range still loads")
    fun reconstitute_acceptsAnInvertedPriceRange() {
        assertThatThrownBy { PriceRange(BigDecimal("900"), BigDecimal("800")) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        val loaded = PriceRange.reconstitute(BigDecimal("900"), BigDecimal("800"))

        assertThat(loaded.min).isEqualByComparingTo(BigDecimal("900"))
        assertThat(loaded.max).isEqualByComparingTo(BigDecimal("800"))
    }

    /**
     * `of` rejects a half-specified range; the read path never sees one.
     *
     * The nullable pairing lives in `MasterLeasingContractMapper.readPriceRange` rather than on
     * `PriceRange`, and that KDoc records why: a companion helper calling `reconstitute` from
     * inside `core.domain` fails `ClassRoleRulesTest`, and naming it `reconstituteOptional`
     * would have escaped the rule's exact-name match and left the read path unguarded.
     */
    @Test
    @DisplayName("The write path rejects a half-specified range")
    fun of_rejectsAHalfSpecifiedRange() {
        assertThatThrownBy { PriceRange.of(BigDecimal("749"), null) }
            .isInstanceOf(InvalidMasterLeasingContractException::class.java)

        assertThat(PriceRange.of(null, null)).isNull()
    }

    @Test
    @DisplayName("Blank identities and terms still load")
    fun reconstitute_acceptsBlankValuesTheWritePathRejects() {
        assertThatCode {
            EmployerId.reconstitute(" ")
            LessorId.reconstitute("")
            PartnerNumber.reconstitute(" ")
            ContractType.reconstitute("")
            EligibleEmployees.reconstitute(0)
        }.doesNotThrowAnyException()
    }

    /**
     * The write path is still guarded, which is the half a bypass could quietly break.
     *
     * `@ConsistentCopyVisibility` makes the generated `copy()` private along with the primary
     * constructor, so `CurrencyCode("EUR").copy(value = "eur")` does not compile. That closes a
     * hole the original public-constructor version had — before this change, `copy` was the
     * validating path's back door and nothing had noticed.
     *
     * Asserted here rather than trusted because the whole point of moving validation into a
     * companion `invoke` is that the constructor stops being the guard, and a test that only
     * checked `reconstitute` would pass just as happily if `invoke` had stopped validating too.
     *
     * **And it must name the domain exception, not `RuntimeException`.** The first version of
     * this file expected the supertype at all ten write-path sites, which `ddd-hex-reviewer`
     * caught under `test.definition.md` § 3.2. The widening bit hardest exactly here: this
     * assertion exists to detect `invoke` having stopped validating, and against
     * `RuntimeException` it would have passed on an NPE or a `ClassCastException` — the one
     * guard in the increment that could not tell validation from an accident.
     */
    @Test
    @DisplayName("The write path still validates — reconstitute is the only bypass")
    fun invoke_stillValidates_soReconstituteIsTheOnlyWayPast() {
        assertThatThrownBy { CurrencyCode("eur") }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
        assertThatThrownBy { EmployerId(" ") }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
        assertThatThrownBy {
            CreditLimit(BigDecimal("-1"))
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
        assertThatThrownBy { EligibleEmployees(0) }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
        assertThatThrownBy {
            Percentage(BigDecimal("101"))
        }.isInstanceOf(InvalidMasterLeasingContractException::class.java)
    }
}
