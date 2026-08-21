package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import java.time.Instant;

/**
 * Domain event emitted when the guide cancelled the tour, cascading to its bookings (UC09).
 *
 * <p>Immutable. Published after successful transaction commit (ADR 0002).
 *
 * <p>Replaces the undifferentiated {@code TourBookingCancelled}, which recorded that a
 * booking was cancelled but not by whom — leaving participant- and guide-initiated
 * cancellations indistinguishable to any downstream consumer. The attribution is carried
 * in the event <em>type</em> rather than as a {@code CancelledBy} field, so a consumer
 * subscribes to the fact it cares about instead of filtering.
 *
 * <p>{@code reason} may be null: cancelling without giving one is permitted.
 * {@code guideTourId} may be null too — it is a correlation id, and a caller without one
 * may still cancel. Both are plain nullable types rather than {@code Optional} because a
 * record component is a field, not a query ({@code coding-style.definition.md} § 1.4
 * exception).
 *
 * <p>{@code guideTourId} is carried here and <b>not stored</b> on the aggregate: no
 * invariant needs it, and UC06/UC07 treat the same id the same way, so a column would make
 * cancellation the lone exception (UC09 § 6). It is a plain {@link String} because the
 * identity is owned by the {@code guide} context (ADR-0005 category 2).
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}.
 */
public record BookingCancelledByGuide(
        BookingId bookingId,
        Instant cancelledAt,
        CancellationReason reason,
        String guideTourId
) implements DomainEvent {
}
