package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;

import java.time.Instant;

/**
 * Domain event emitted when a tour booking is marked active.
 *
 * <p>Immutable. Published after successful transaction commit.
 * {@code guideTourId} is an optional correlation identifier linking the booking
 * to the guide tour execution that triggered the activation.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}.
 */
public record BookingActivated(
        BookingId bookingId,
        Instant activatedAt,
        String guideTourId
) implements DomainEvent {
}
