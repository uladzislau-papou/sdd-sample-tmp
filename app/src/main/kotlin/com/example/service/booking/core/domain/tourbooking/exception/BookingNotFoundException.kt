package com.example.service.booking.core.domain.tourbooking.exception

/**
 * Thrown when no booking exists for a given identity.
 *
 * Maps to HTTP 404 (`coding-style.definition.md` § 6.2).
 */
class BookingNotFoundException(
    bookingId: String,
) : RuntimeException("Booking not found: $bookingId")
