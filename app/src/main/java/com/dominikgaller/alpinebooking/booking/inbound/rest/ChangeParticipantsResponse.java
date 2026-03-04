package com.dominikgaller.alpinebooking.booking.inbound.rest;

/**
 * Response DTO returned by the UC04 – ChangeParticipants endpoint.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}, section 3.
 */
public record ChangeParticipantsResponse(int participantCount) {
}
