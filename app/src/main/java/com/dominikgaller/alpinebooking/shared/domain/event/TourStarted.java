package com.dominikgaller.alpinebooking.shared.domain.event;

import com.dominikgaller.alpinebooking.shared.domain.TourId;

import java.time.Instant;

/**
 * Integration event emitted by the {@code guide} bounded context when a guide tour is started.
 *
 * <p>Lives in {@code shared.domain.event} so the {@code booking} context can subscribe
 * without depending on the {@code guide} package. {@code guideTourId} is carried as a
 * plain {@link String} to avoid a {@code shared → guide} compile-time dependency.
 *
 * <p>Immutable. Published after the guide tour's transaction commits (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 6,
 *          and {@code documentation/adr/0005-bounded-context-identity-boundaries.adr.md}
 *          for the foreign-identity rule.
 */
public record TourStarted(
        String guideTourId,
        TourId tourId,
        Instant startedAt
) implements DomainEvent {
}
