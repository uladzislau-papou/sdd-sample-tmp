package com.example.service.booking.core.domain.tourbooking.exception

/**
 * Thrown when a booking request violates a creation-time invariant.
 *
 * Maps to HTTP 400 (`coding-style.definition.md` § 6.2).
 */
class InvalidBookingRequestException(
    message: String,
) : RuntimeException(message)
