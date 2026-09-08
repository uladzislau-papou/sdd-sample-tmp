package com.example.contractmanagement.booking.core.domain.tourbooking.event

import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantCount
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.shared.domain.event.DomainEvent
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
