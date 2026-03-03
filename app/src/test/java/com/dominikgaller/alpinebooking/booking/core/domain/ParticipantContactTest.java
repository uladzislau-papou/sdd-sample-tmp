package com.dominikgaller.alpinebooking.booking.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
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
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParticipantContact("  ", "alice@example.com"));
    }

    @Test
    void emptyNameThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParticipantContact("", "alice@example.com"));
    }

    @Test
    void blankEmailThrows() {
        assertThatIllegalArgumentException()
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
