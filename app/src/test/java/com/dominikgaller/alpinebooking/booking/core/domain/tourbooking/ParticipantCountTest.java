package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ParticipantCountTest {

    @Test
    void value1IsValid() {
        assertThat(new ParticipantCount(1).value()).isEqualTo(1);
    }

    @Test
    void largeValueIsValid() {
        assertThat(new ParticipantCount(100).value()).isEqualTo(100);
    }

    @Test
    void value0Throws() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new ParticipantCount(0));
    }

    @Test
    void negativeValueThrows() {
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> new ParticipantCount(-5));
    }
}
