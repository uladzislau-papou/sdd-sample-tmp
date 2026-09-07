package com.example.service.booking.core.domain.tourbooking.exception

/**
 * Thrown when the availability system cannot be reached or answers unusably.
 *
 * This is not a domain rule being violated — it is a dependency failing — so it maps to
 * HTTP 502 rather than to 400 or 409.
 */
class AvailabilityUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
