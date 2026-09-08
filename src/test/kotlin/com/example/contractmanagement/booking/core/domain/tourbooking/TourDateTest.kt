package com.example.contractmanagement.booking.core.domain.tourbooking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class TourDateTest {
    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")

    @Test
    fun isInFuture_isTrue_forATomorrowDate() {
        assertThat(TourDate(LocalDate.parse("2026-06-02")).isInFuture(now)).isTrue()
    }

    @Test
    fun isInFuture_isFalse_forTheSameDay() {
        assertThat(TourDate(LocalDate.parse("2026-06-01")).isInFuture(now)).isFalse()
    }

    @Test
    fun isInFuture_isFalse_forAPastDate() {
        assertThat(TourDate(LocalDate.parse("2026-05-31")).isInFuture(now)).isFalse()
    }
}
