package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TourDateTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Test
    void isInFutureReturnsTrueForTomorrow() {
        final TourDate tomorrow = new TourDate(
                NOW.plus(1, ChronoUnit.DAYS).atZone(java.time.ZoneOffset.UTC).toLocalDate());
        assertThat(tomorrow.isInFuture(NOW)).isTrue();
    }

    @Test
    void isInFutureReturnsFalseForYesterday() {
        final TourDate yesterday = new TourDate(
                NOW.minus(1, ChronoUnit.DAYS).atZone(java.time.ZoneOffset.UTC).toLocalDate());
        assertThat(yesterday.isInFuture(NOW)).isFalse();
    }

    @Test
    void isInFutureReturnsFalseForToday() {
        final TourDate today = new TourDate(
                NOW.atZone(java.time.ZoneOffset.UTC).toLocalDate());
        assertThat(today.isInFuture(NOW)).isFalse();
    }

    @Test
    void nullDateThrows() {
        assertThatNullPointerException().isThrownBy(() -> new TourDate(null));
    }

    @Test
    void nullNowThrows() {
        final TourDate date = new TourDate(java.time.LocalDate.of(2030, 1, 1));
        assertThatNullPointerException().isThrownBy(() -> date.isInFuture(null));
    }
}
