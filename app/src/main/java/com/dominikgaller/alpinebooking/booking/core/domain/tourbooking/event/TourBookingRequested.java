package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.shared.domain.TourId;

import java.time.Instant;

/**
 * Domain event emitted when a new tour booking is requested.
 *
 * <p>Immutable. Published after successful transaction commit (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 5.
 */
public record TourBookingRequested(
        BookingId bookingId,
        TourId tourId,
        TourDate tourDate,
        ParticipantCount participantCount,
        Instant occurredAt
) implements DomainEvent {
}
