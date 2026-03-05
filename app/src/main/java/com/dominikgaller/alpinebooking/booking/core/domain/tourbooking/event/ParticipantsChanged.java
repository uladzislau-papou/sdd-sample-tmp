package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;

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
