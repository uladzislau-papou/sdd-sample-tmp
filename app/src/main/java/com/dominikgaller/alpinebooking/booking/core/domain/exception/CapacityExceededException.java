package com.dominikgaller.alpinebooking.booking.core.domain.exception;

/**
 * Thrown when the requested participant count exceeds the available capacity for a tour.
 *
 * <p>Maps to HTTP 409 Conflict at the REST boundary.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2.
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(final int requested, final int available) {
        super(String.format(
                "Requested %d participants exceeds available capacity of %d",
                requested, available));
    }
}
