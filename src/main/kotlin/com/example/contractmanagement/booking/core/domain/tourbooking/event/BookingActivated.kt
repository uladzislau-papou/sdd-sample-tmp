package com.example.contractmanagement.booking.core.domain.tourbooking.event

import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * A confirmed booking became active because its tour started (UC06).
 *
 * @property guideTourId correlation id of the guide tour execution that caused this.
 *   Null when the activation had no guide tour to correlate with. Deliberately a plain
 *   [String]: the identity is owned by the `guide` context, so `booking` treats it as
 *   opaque and never parses or branches on it
 *   (`documentation/adr/0005-bounded-context-identity-boundaries.adr.md`).
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md` § 6.
 */
data class BookingActivated(
    val bookingId: BookingId,
    val activatedAt: Instant,
    val guideTourId: String?,
) : DomainEvent
