package com.dominikgaller.alpinebooking.shared.domain.event;

import com.dominikgaller.alpinebooking.shared.domain.TourId;

import java.time.Instant;

/**
 * Integration event: a guide called off a tour execution (UC12).
 *
 * <p>Lives in {@code shared.domain.event} because it crosses a context boundary, like
 * {@link TourStarted} and {@link TourCompleted}. Published post-commit (ADR-0002), so it
 * cannot announce a cancellation that rolled back.
 *
 * <p>Note this event is a <em>notification</em>, not the mechanism. The bookings attached to
 * the tour are cancelled synchronously through {@code booking}'s inport during the
 * transaction (UC09), because the guide needs confirmation before reporting the tour
 * cancelled. Anything that merely wants to know a tour was called off — a read model, a
 * notification service — listens to this instead.
 *
 * <p>{@code reason} may be null: cancelling without giving one is permitted. Nullable rather
 * than {@code Optional} because a record component is a field, not a query
 * ({@code coding-style.definition.md} § 1.4 exception).
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}.
 */
public record TourCancelledByGuide(
        String guideTourId,
        TourId tourId,
        Instant cancelledAt,
        String reason
) implements DomainEvent {
}
