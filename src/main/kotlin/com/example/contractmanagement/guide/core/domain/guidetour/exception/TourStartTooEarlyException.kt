package com.example.contractmanagement.guide.core.domain.guidetour.exception

import java.time.Instant

/**
 * Thrown when a guide tries to start a tour before its scheduled time. Maps to 409.
 *
 * A conflict rather than a bad request: the input is well-formed, the world is not ready.
 */
class TourStartTooEarlyException(
    scheduledStart: Instant,
    attemptedStart: Instant,
) : RuntimeException("Tour is scheduled for $scheduledStart and cannot start at $attemptedStart")
