package com.dominikgaller.alpinebooking.booking.inbound.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * REST request body for UC04 – ChangeParticipants.
 *
 * <p>Bean Validation constraints enforce syntactic rules at the HTTP boundary.
 * Semantic rules (capacity check) are enforced by the domain.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}, section 2.
 */
public record ChangeParticipantsRequest(
        @NotNull @Min(1) Integer newParticipantCount
) {
}
