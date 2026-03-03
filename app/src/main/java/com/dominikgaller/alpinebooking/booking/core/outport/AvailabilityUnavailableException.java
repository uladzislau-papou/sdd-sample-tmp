package com.dominikgaller.alpinebooking.booking.core.outport;

/**
 * Thrown by {@link AvailabilityChecker} when the availability source cannot be reached
 * or returns an invalid response.
 *
 * <p>Maps to HTTP 502 Bad Gateway at the REST boundary.
 *
 * <p>SDD: See {@code documentation/ports/availability-checker.outport.spec.md}, section 2.1.
 */
public class AvailabilityUnavailableException extends RuntimeException {

    public AvailabilityUnavailableException(final String message) {
        super(message);
    }

    public AvailabilityUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
