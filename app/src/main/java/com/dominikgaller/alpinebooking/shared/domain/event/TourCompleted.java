package com.dominikgaller.alpinebooking.shared.domain.event;

import com.dominikgaller.alpinebooking.shared.domain.TourId;

import java.time.Instant;

/**
 * Integration event emitted by the {@code guide} bounded context when a guide tour is
 * completed (UC11).
 *
 * <p>Lives in {@code shared.domain.event} so the {@code booking} context can subscribe
 * without depending on the {@code guide} package. {@code guideTourId} is carried as a
 * plain {@link String} to avoid a {@code shared → guide} dependency and because the
 * identity is owned by {@code guide}
 * ({@code documentation/adr/0005-bounded-context-identity-boundaries.adr.md}, category 2).
 *
 * <p>Immutable. Published after the guide tour's transaction commits (ADR 0002).
 * Consumed by {@code booking} to complete the affected bookings (UC07).
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 6.
 */
public record TourCompleted(
        String guideTourId,
        TourId tourId,
        Instant completedAt
) implements DomainEvent {
}
