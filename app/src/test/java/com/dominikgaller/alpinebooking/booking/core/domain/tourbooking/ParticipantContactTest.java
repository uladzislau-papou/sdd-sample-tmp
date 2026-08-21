package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ParticipantContactTest {

    @Test
    void validNameAndEmailConstructs() {
        final ParticipantContact contact = new ParticipantContact("Alice", "alice@example.com");
        assertThat(contact.name()).isEqualTo("Alice");
        assertThat(contact.email()).isEqualTo("alice@example.com");
    }

    @Test
    void blankNameThrows() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new ParticipantContact("  ", "alice@example.com"));
    }

    @Test
    void emptyNameThrows() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new ParticipantContact("", "alice@example.com"));
    }

    @Test
    void blankEmailThrows() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new ParticipantContact("Alice", "  "));
    }

    @Test
    void nullNameThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ParticipantContact(null, "alice@example.com"));
    }

    @Test
    void nullEmailThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ParticipantContact("Alice", null));
    }
}
