package com.example.contractmanagement.booking.core.domain.tourbooking

import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ParticipantContactTest {
    @Test
    fun construction_storesNameAndEmail() {
        val contact = ParticipantContact("Ada Lovelace", "ada@example.com")

        assertThat(contact.name).isEqualTo("Ada Lovelace")
        assertThat(contact.email).isEqualTo("ada@example.com")
    }

    @Test
    fun construction_throwsInvalidBookingRequestException_whenNameIsBlank() {
        assertThatThrownBy { ParticipantContact("   ", "ada@example.com") }
            .isInstanceOf(InvalidBookingRequestException::class.java)
    }

    @Test
    fun construction_throwsInvalidBookingRequestException_whenEmailIsBlank() {
        assertThatThrownBy { ParticipantContact("Ada Lovelace", "") }
            .isInstanceOf(InvalidBookingRequestException::class.java)
    }
}
