package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import java.time.Instant;

/**
 * Domain event emitted when a tour booking is marked completed (UC07).
 *
 * <p>Immutable. Published after successful transaction commit (ADR 0002).
 *
 * <p>{@code completedAt} is the guide's fact, arriving via {@code TourCompleted} and passed
 * through. It is carried on the event but <b>not stored</b> on the aggregate — no invariant
 * needs it, mirroring how {@code markActive} treats {@code startedAt}.
 *
 * <p>{@code guideTourId} is an optional correlation id and may be null — a record component
 * is a field, not a query, so {@link java.util.Optional} would be wrong here
 * ({@code documentation/domain/aggregate-guide-tour.spec.md} § 2). It is a plain
 * {@link String} because the identity belongs to the {@code guide} context and this one
 * treats it as opaque (ADR-0005 category 2). Carrying it keeps the booking lifecycle
 * traceable to its guide-side cause at completion as well as at activation, where
 * {@code BookingActivated} carries the same field.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}.
 */
public record BookingCompleted(
        BookingId bookingId,
        Instant completedAt,
        String guideTourId
) implements DomainEvent {
}
