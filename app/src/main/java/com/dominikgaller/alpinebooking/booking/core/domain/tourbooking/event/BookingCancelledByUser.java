package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import java.time.Instant;

/**
 * Domain event emitted when the participant cancelled their own booking (UC08).
 *
 * <p>Immutable. Published after successful transaction commit (ADR 0002).
 *
 * <p>Replaces the undifferentiated {@code TourBookingCancelled}, which recorded that a
 * booking was cancelled but not by whom — leaving participant- and guide-initiated
 * cancellations indistinguishable to any downstream consumer. The attribution is carried
 * in the event <em>type</em> rather than as a {@code CancelledBy} field, so a consumer
 * subscribes to the fact it cares about instead of filtering.
 *
 * <p>{@code reason} may be null: cancelling without giving one is permitted. A record
 * component is a field, not a query, so {@code Optional} would be the wrong choice here
 * ({@code coding-style.definition.md} § 1.4 exception).
 *
 * <p>SDD: See {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}.
 */
public record BookingCancelledByUser(
        BookingId bookingId,
        Instant cancelledAt,
        CancellationReason reason
) implements DomainEvent {
}
