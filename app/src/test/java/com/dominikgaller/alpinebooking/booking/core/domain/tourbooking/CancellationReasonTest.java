package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for the {@link CancellationReason} value object (UC08).
 *
 * <p>The 400-character ceiling is a business rule, so it is enforced here rather than by a
 * Bean Validation annotation on a request DTO: the aggregate must be unable to hold an
 * invalid reason regardless of which adapter supplied it (Always-Valid,
 * {@code modelling.definition.md}).
 *
 * <p>SDD: See {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md} section 2.
 */
class CancellationReasonTest {

    private static final int MAX_LENGTH = 400;

    @Test
    void acceptsOrdinaryText() {
        assertThat(new CancellationReason("Travel plans changed").value())
                .isEqualTo("Travel plans changed");
    }

    @Test
    void acceptsTextOfExactlyMaxLength() {
        final String exactly400 = "x".repeat(MAX_LENGTH);

        assertThat(new CancellationReason(exactly400).value()).hasSize(MAX_LENGTH);
    }

    @Test
    void throwsInvalidBookingRequestException_whenOneCharacterOverMaxLength() {
        final String tooLong = "x".repeat(MAX_LENGTH + 1);

        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new CancellationReason(tooLong))
                .withMessageContaining("400");
    }

    @Test
    void throwsInvalidBookingRequestException_whenBlank() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new CancellationReason("   "));
    }

    @Test
    void throwsInvalidBookingRequestException_whenEmpty() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new CancellationReason(""));
    }

    /**
     * Null is a programmer error, not a business outcome — an absent reason is represented
     * by not constructing the object at all ({@code coding-style.definition.md} § 6.2).
     */
    @Test
    void throwsNullPointerException_whenNull() {
        assertThatNullPointerException().isThrownBy(() -> new CancellationReason(null));
    }

    /**
     * Length is counted on the raw value, so trailing whitespace cannot be used to smuggle
     * a 401st character past the guard by being trimmed away afterwards.
     */
    @Test
    void countsLengthBeforeTrimming() {
        final String padded = "x".repeat(MAX_LENGTH) + "   ";

        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new CancellationReason(padded));
    }
}
