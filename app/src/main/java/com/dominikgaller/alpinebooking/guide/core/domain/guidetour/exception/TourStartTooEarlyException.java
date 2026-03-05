package com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception;

import java.time.Instant;

/**
 * Thrown when a guide tour start is attempted before the scheduled start time.
 *
 * <p>Maps to HTTP 409 Conflict at the REST boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 8.
 */
public class TourStartTooEarlyException extends RuntimeException {

    public TourStartTooEarlyException(final Instant scheduledStart, final Instant attemptedAt) {
        super("Tour cannot be started before scheduled start time. Scheduled: "
                + scheduledStart + ", attempted: " + attemptedAt);
    }
}
