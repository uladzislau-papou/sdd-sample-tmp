package com.dominikgaller.alpinebooking.guide.core.domain.guidetour;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidCancellationReasonException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@code guide}'s own {@link CancellationReason} value object (UC12).
 *
 * <p>This type exists because {@code guide} <em>stores</em> the reason — {@code GuideTour}
 * holds it and {@code guide_tour.cancellation_reason} is a sized column — so this context
 * owns an invariant about it and must enforce it in the domain, not in a driver
 * ({@code coding-style.definition.md} § 6.2 clause A, {@code modelling.definition.md}
 * § Always-Valid). Before this, {@code CancelTourByGuideDriver} validated inline and threw
 * {@code IllegalArgumentException}, which left {@code GuideTour.cancel} able to build a
 * valid-by-construction aggregate that could not be persisted.
 *
 * <p>Deliberately a separate type from {@code booking}'s {@code CancellationReason} rather
 * than a shared one: a domain value object must not cross a context boundary
 * ({@code architecture.definition.md} § 11 rule 3). Only the {@code String} crosses.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md} section 2.
 */
class CancellationReasonTest {

    @Test
    void acceptsOrdinaryText() {
        assertThat(new CancellationReason("Severe weather warning").value())
                .isEqualTo("Severe weather warning");
    }

    @Test
    void acceptsTextOfExactlyMaxLength() {
        final String exact = "x".repeat(CancellationReason.MAX_LENGTH);

        assertThat(new CancellationReason(exact).value())
                .hasSize(CancellationReason.MAX_LENGTH);
    }

    @Test
    void throwsInvalidCancellationReasonException_whenOneCharacterOverMaxLength() {
        final String tooLong = "x".repeat(CancellationReason.MAX_LENGTH + 1);

        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> new CancellationReason(tooLong))
                .withMessageContaining("400");
    }

    @Test
    void throwsInvalidCancellationReasonException_whenBlank() {
        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> new CancellationReason("   "));
    }

    @Test
    void throwsNullPointerException_whenNull() {
        assertThatNullPointerException().isThrownBy(() -> new CancellationReason(null));
    }

    @Test
    void countsLengthBeforeTrimming() {
        final String padded = "x".repeat(CancellationReason.MAX_LENGTH) + "   ";

        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> new CancellationReason(padded));
    }

    /**
     * The two contexts must agree on the ceiling without sharing the type: if they drifted,
     * {@code guide} would accept a reason {@code booking} then rejected mid-transaction,
     * turning a 400 into a 502.
     *
     * <p>Asserted against a duplicated literal rather than against
     * {@code booking.core.domain.tourbooking.CancellationReason.MAX_LENGTH}, which is how an
     * earlier revision did it. Referencing the other context's constant from here relocates
     * the cross-context dependency into test code instead of removing it, and whether
     * § 11 rule 3 binds tests is an open question rather than a settled yes — so this does
     * not lean on the answer. {@code booking}'s own {@code CancellationReasonTest} duplicates
     * the literal for the same reason.
     *
     * <p>The cost is that this test cannot fail when only the *other* side changes. What it
     * does catch is this side drifting, which is the direction this context controls.
     */
    @Test
    void ceilingIsFourHundred() {
        assertThat(CancellationReason.MAX_LENGTH).isEqualTo(400);
    }
}
