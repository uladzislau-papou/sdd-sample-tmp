package com.dominikgaller.alpinebooking.booking.core.domain.event;

import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantCount;

import java.time.Instant;

/**
 * Domain event emitted when the participant count of a tour booking is changed.
 *
 * <p>Immutable. Published after successful transaction commit (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}, section 5.
 */
public record ParticipantsChanged(
        BookingId bookingId,
        ParticipantCount newParticipantCount,
        Instant occurredAt
) implements DomainEvent {
}
