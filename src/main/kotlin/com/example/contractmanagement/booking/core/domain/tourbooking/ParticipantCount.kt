package com.example.contractmanagement.booking.core.domain.tourbooking

import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException

/**
 * How many people a booking is for. Always at least one.
 *
 * Throws a domain exception rather than [IllegalArgumentException] because this value
 * arrives from a command, so the value object is the last guard that is guaranteed to run
 * (`coding-style.definition.md` § 6.2, clause one).
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
data class ParticipantCount(
    val value: Int,
) {
    init {
        if (value < 1) {
            throw InvalidBookingRequestException("Participant count must be >= 1, was: $value")
        }
    }
}
