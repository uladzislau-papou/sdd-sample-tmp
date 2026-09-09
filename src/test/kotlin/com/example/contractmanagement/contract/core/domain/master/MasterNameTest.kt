package com.example.contractmanagement.contract.core.domain.master

import com.example.contractmanagement.contract.core.domain.master.exception.InvalidMasterException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * I-01 — `name` is non-blank after trimming and at most 200 characters.
 *
 * SDD: `documentation/domain/aggregate-master.spec.md` § 2 (I-01) and
 * `documentation/use-cases/uc01-create-master.spec.md` § 7 (AC-03).
 *
 * One case per rejection reason AC-03 names — blank, whitespace-only, over 200 characters —
 * plus the boundary that proves the limit is inclusive and the normalisation the invariant's
 * "after trimming" wording implies.
 */
class MasterNameTest {
    @Test
    @DisplayName("I-01: a blank name is rejected")
    fun blankNameThrows() {
        assertThatThrownBy { MasterName("") }
            .isInstanceOf(InvalidMasterException::class.java)
    }

    @Test
    @DisplayName("I-01: a whitespace-only name is rejected")
    fun whitespaceOnlyNameThrows() {
        assertThatThrownBy { MasterName("   ") }
            .isInstanceOf(InvalidMasterException::class.java)
    }

    @Test
    @DisplayName("I-01: a name longer than 200 characters is rejected")
    fun nameLongerThanTheLimitThrows() {
        assertThatThrownBy { MasterName("A".repeat(201)) }
            .isInstanceOf(InvalidMasterException::class.java)
    }

    /**
     * The bound measures the string **as supplied**, not its trimmed form. I-01's "after
     * trimming" qualifies the blank check only — the same reading [nameIsKeptAsGiven] pins for
     * storage — so 201 characters of which two are padding is 201 characters and is rejected.
     *
     * Without this case the raw-versus-trimmed choice is invisible: every other rejection case
     * has nothing to trim, so all of them pass under either reading.
     */
    @Test
    @DisplayName("I-01: the 200-character bound measures the raw value, not the trimmed one")
    fun nameLongerThanTheLimitOnlyBeforeTrimmingThrows() {
        assertThatThrownBy { MasterName("  " + "A".repeat(199)) }
            .isInstanceOf(InvalidMasterException::class.java)
    }

    @Test
    @DisplayName("I-01: the 200-character limit is inclusive")
    fun nameOfExactlyTheLimitIsAccepted() {
        assertThat(MasterName("A".repeat(200)).value).hasSize(200)
    }

    /**
     * I-01 says the name is "non-blank after trimming and at most 200 characters". It does
     * **not** say the value object stores the trimmed form, so it does not: trimming
     * qualifies the blank check and nothing else. Normalising would be behaviour no
     * specification asked for.
     *
     * This case pins that reading so the choice is covered rather than silent. If the domain
     * spec later decides a Master name is canonicalised, this is the test that has to change,
     * and changing it is a visible decision.
     */
    @Test
    @DisplayName("I-01: trimming qualifies the blank check only — the value is kept as given")
    fun nameIsKeptAsGiven() {
        assertThat(MasterName("  Acme Manufacturing  ").value).isEqualTo("  Acme Manufacturing  ")
    }
}
