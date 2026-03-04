package com.dominikgaller.alpinebooking.booking.core.domain.exception;

/**
 * Thrown when a booking with the given ID cannot be found in the repository.
 *
 * <p>Maps to HTTP 404 Not Found at the REST boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 9.
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(final String bookingId) {
        super("Booking not found: " + bookingId);
    }
}
