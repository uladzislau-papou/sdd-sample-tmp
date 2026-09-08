package com.example.contractmanagement.booking.core.inport.command

import java.time.Instant

/**
 * Input for the MarkBookingActive use case (UC06).
 *
 * @property startedAt when the tour began. Null means "the caller has no authoritative
 *   time", and the driver then reads `ClockPort`. A caller relaying a domain event has
 *   one; a caller triggering the transition directly does not.
 * @property guideTourId opaque correlation id owned by the `guide` context; null when the
 *   activation has no guide tour to correlate with.
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md` § 2.
 */
data class MarkBookingActiveCommand(
    val bookingId: String,
    val startedAt: Instant?,
    val guideTourId: String?,
)
