package com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception;

import java.time.Instant;

/**
 * Thrown when a guide tour is completed at a moment before it started.
 *
 * <p>Maps to HTTP 409 Conflict — the request is well formed but contradicts the
 * aggregate's current state.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 3.
 */
public class TourCompletedBeforeStartException extends RuntimeException {

    public TourCompletedBeforeStartException(final Instant startedAt, final Instant attemptedAt) {
        super("Tour cannot be completed before it started. Started: "
                + startedAt + ", attempted: " + attemptedAt);
    }
}
