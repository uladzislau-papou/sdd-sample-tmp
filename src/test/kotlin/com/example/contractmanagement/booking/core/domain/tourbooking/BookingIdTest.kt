package com.example.contractmanagement.booking.core.domain.tourbooking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class BookingIdTest {
    @Test
    fun of_wrapsTheGivenValue() {
        val uuid = UUID.fromString("22222222-2222-2222-2222-222222222222")

        assertThat(BookingId.of(uuid).value).isEqualTo(uuid)
    }

    @Test
    fun generate_producesADistinctValue_onEachCall() {
        assertThat(BookingId.generate()).isNotEqualTo(BookingId.generate())
    }

    @Test
    fun equality_isStructural() {
        val uuid = UUID.randomUUID()

        assertThat(BookingId.of(uuid)).isEqualTo(BookingId.of(uuid))
    }
}
