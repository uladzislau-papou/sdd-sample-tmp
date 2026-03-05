package com.dominikgaller.alpinebooking.booking.core.domain.event;

import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourId;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import java.time.Instant;

/**
 * Domain event emitted when a guide tour is started.
 *
 * <p>Immutable. Published after successful transaction commit.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 6.
 */
public record TourStarted(
        GuideTourId guideTourId,
        TourId tourId,
        Instant startedAt
) implements DomainEvent {
}
