package com.example.contractmanagement.booking.core.domain.tourbooking

import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException

/**
 * Who to contact about a booking.
 *
 * Constructed from a command, so invariant violations raise a domain exception
 * (`coding-style.definition.md` § 6.2, clause one).
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
data class ParticipantContact(
    val name: String,
    val email: String,
) {
    init {
        if (name.isBlank()) {
            throw InvalidBookingRequestException("Contact name must not be blank")
        }
        if (email.isBlank()) {
            throw InvalidBookingRequestException("Contact email must not be blank")
        }
    }
}
