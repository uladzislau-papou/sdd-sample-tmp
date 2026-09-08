package com.example.contractmanagement.booking.core.domain.tourbooking

import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ParticipantCountTest {
    @Test
    fun construction_succeeds_forTheSmallestValidCount() {
        assertThat(ParticipantCount(1).value).isEqualTo(1)
    }

    @Test
    fun construction_throwsInvalidBookingRequestException_whenCountIsZero() {
        assertThatThrownBy { ParticipantCount(0) }
            .isInstanceOf(InvalidBookingRequestException::class.java)
    }

    @Test
    fun construction_throwsInvalidBookingRequestException_whenCountIsNegative() {
        assertThatThrownBy { ParticipantCount(-1) }
            .isInstanceOf(InvalidBookingRequestException::class.java)
    }
}
