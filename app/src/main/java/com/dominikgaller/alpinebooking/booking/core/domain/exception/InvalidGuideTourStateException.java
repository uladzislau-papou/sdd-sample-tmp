package com.dominikgaller.alpinebooking.booking.core.domain.exception;

import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourStatus;

/**
 * Thrown when a state transition is attempted on a guide tour that is not in
 * the required state.
 *
 * <p>Maps to HTTP 409 Conflict at the REST boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 8.
 */
public class InvalidGuideTourStateException extends RuntimeException {

    public InvalidGuideTourStateException(final GuideTourStatus currentStatus) {
        super("Invalid state transition from state: " + currentStatus.name());
    }
}
