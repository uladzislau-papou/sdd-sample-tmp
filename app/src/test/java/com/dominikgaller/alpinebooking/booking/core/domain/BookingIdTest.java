package com.dominikgaller.alpinebooking.booking.core.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BookingIdTest {

    @Test
    void constructsWithValidUuid() {
        final UUID uuid = UUID.randomUUID();
        final BookingId id = BookingId.of(uuid);
        assertThat(id.value()).isEqualTo(uuid);
    }

    @Test
    void generateReturnsNonNull() {
        assertThat(BookingId.generate()).isNotNull();
        assertThat(BookingId.generate().value()).isNotNull();
    }

    @Test
    void equalsByValue() {
        final UUID uuid = UUID.randomUUID();
        assertThat(BookingId.of(uuid)).isEqualTo(BookingId.of(uuid));
    }

    @Test
    void nullValueThrows() {
        assertThatNullPointerException().isThrownBy(() -> BookingId.of(null));
    }
}
