package com.example.service.booking.core.domain.tourbooking.exception

import com.example.service.booking.core.domain.tourbooking.TourBookingStatus

/**
 * Thrown when a state transition is attempted from a state that does not permit it.
 *
 * Maps to HTTP 409 (`coding-style.definition.md` § 6.2).
 */
class InvalidBookingStateException(
    currentStatus: TourBookingStatus,
) : RuntimeException("Invalid state transition from state: $currentStatus")
