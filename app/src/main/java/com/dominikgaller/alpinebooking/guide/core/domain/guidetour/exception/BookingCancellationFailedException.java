package com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception;

/**
 * The {@code booking} context refused or failed to cancel the bookings for a tour the guide
 * is cancelling (UC12).
 *
 * <p>Maps to HTTP 502: the guide's own request was well-formed and its tour was cancellable,
 * but a downstream context this use case depends on did not complete. Distinguishing it from
 * a 409 matters — a 409 means "you cannot do that", a 502 means "try again".
 *
 * <p>Lives in {@code guide}'s exception package although it describes a failure originating
 * in {@code booking}: it is the {@code guide} context's interpretation of that failure, and
 * naming it here keeps {@code guide} from having to expose {@code booking}'s exception types
 * through its own REST contract.
 *
 * <p>Carries the cause so the original failure is not lost — the transaction is rolling back
 * either way, and a 502 with no explanation is nearly impossible to diagnose.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md} section 3.
 */
public class BookingCancellationFailedException extends RuntimeException {

    public BookingCancellationFailedException(final String tourId, final Throwable cause) {
        super("Failed to cancel the bookings for tour " + tourId
                + "; the tour cancellation was rolled back", cause);
    }
}
