package com.dominikgaller.alpinebooking.booking.core.domain.exception;

import com.dominikgaller.alpinebooking.booking.core.domain.TourBookingStatus;

/**
 * Thrown when a state transition is attempted on a booking that is not in
 * the required state.
 *
 * <p>Maps to HTTP 409 Conflict at the REST boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 9.
 */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(final TourBookingStatus currentStatus) {
        super("Cannot confirm booking in state: " + currentStatus.name());
    }
}
