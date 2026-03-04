package com.dominikgaller.alpinebooking.booking.core.domain.event;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;

import java.time.Instant;

/**
 * Domain event emitted when a tour booking is cancelled.
 *
 * <p>Immutable. Published after successful transaction commit (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancle-tour-booking.spec.md}, section 7.
 */
public record TourBookingCancelled(
        BookingId bookingId,
        Instant occurredAt
) implements DomainEvent {
}
