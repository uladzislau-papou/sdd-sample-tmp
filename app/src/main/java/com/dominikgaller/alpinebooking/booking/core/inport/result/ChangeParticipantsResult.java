package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Result returned by the ChangeParticipants use case.
 *
 * <p>Contains only standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}, section 3.
 */
public record ChangeParticipantsResult(int participantCount) {
}
