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
 * <p>{@code guideTourId} <b>may be null</b> — a caller without a correlation id may still
 * activate a booking. Nullable rather than {@link java.util.Optional} because a record
 * component is a field, not a query ({@code coding-style.definition.md} § 1.4 exception).
 *
 * <p>It is a plain {@link String} by design — the identity is owned by the {@code guide}
 * context and is opaque here (ADR-0005 category 2).
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}
 *          and {@code documentation/adr/0005-bounded-context-identity-boundaries.adr.md}.
 */
public record BookingActivated(
        BookingId bookingId,
        Instant activatedAt,
        String guideTourId
) implements DomainEvent {
}
