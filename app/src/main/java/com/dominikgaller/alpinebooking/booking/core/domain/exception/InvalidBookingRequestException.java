package com.dominikgaller.alpinebooking.booking.core.domain.exception;

/**
 * Thrown when a booking request violates structural or temporal constraints.
 *
 * <p>Maps to HTTP 400 Bad Request at the REST boundary.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2.
 */
public class InvalidBookingRequestException extends RuntimeException {

    public InvalidBookingRequestException(final String message) {
        super(message);
    }
}
