package com.dominikgaller.alpinebooking.booking.core.inport;

/**
 * Command carrying the input data for the ChangeParticipants use case.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}, section 2.
 */
public record ChangeParticipantsCommand(String bookingId, int newParticipantCount) {
}
