package com.example.service.booking.core.domain.tourbooking.event

import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * A requested booking was confirmed (UC02).
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md` § 6.
 */
data class TourBookingConfirmed(
    val bookingId: BookingId,
    val occurredAt: Instant,
) : DomainEvent
