package com.example.service.booking.core.domain.tourbooking.event

import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.ParticipantCount
import com.example.service.booking.core.domain.tourbooking.TourDate
import com.example.service.shared.domain.TourId
import com.example.service.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * A booking was requested (UC01).
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 6.
 */
data class TourBookingRequested(
    val bookingId: BookingId,
    val tourId: TourId,
    val tourDate: TourDate,
    val participantCount: ParticipantCount,
    val occurredAt: Instant,
) : DomainEvent
