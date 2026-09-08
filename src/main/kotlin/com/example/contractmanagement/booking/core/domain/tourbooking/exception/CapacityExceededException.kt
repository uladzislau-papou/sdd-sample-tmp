package com.example.contractmanagement.booking.core.domain.tourbooking.exception

/**
 * Thrown when the requested participant count exceeds the capacity available at the time
 * of the request.
 *
 * Maps to HTTP 409 (`coding-style.definition.md` § 6.2): the request is well-formed, the
 * world simply does not allow it right now.
 */
class CapacityExceededException(
    requested: Int,
    available: Int,
) : RuntimeException("Requested $requested participants exceeds available capacity of $available")
