package com.dominikgaller.alpinebooking.booking.core.domain.event;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;

import java.time.Instant;

/**
 * Domain event emitted when a tour booking is confirmed.
 *
 * <p>Immutable. Published after successful transaction commit (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 7.
 */
public record TourBookingConfirmed(
        BookingId bookingId,
        Instant occurredAt
) implements DomainEvent {
}
