package com.dominikgaller.alpinebooking.booking.inbound.rest.request;

import java.time.Instant;

/**
 * REST request body for UC03/UC08 – cancelling a tour booking.
 *
 * <p>Both fields are optional, and the body itself may be omitted entirely — a bare
 * {@code POST /bookings/{id}/cancel} is the original UC03 behaviour, preserved.
 *
 * <p>Deliberately carries <b>no</b> Bean Validation constraints, unlike
 * {@link ChangeParticipantsRequest}. The length and blankness rules for {@code reason} are
 * business rules, so they live in the {@code CancellationReason} value object: UC09 reaches
 * the same aggregate method from the {@code guide} context with no DTO in the path, and a
 * {@code @Size} annotation here would leave that route unguarded. Duplicating the rule in
 * both places would let the two drift.
 *
 * <p>SDD: See {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}, section 9.
 */
public record CancelTourBookingRequest(
        Instant cancelledAt,
        String reason
) {
}
