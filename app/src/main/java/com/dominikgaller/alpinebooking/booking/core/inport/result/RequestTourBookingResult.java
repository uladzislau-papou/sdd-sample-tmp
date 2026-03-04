package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Result returned after a successful RequestTourBooking use case execution.
 *
 * <p>Contains only standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/ports/request-tour-booking.inport.spec.md}, section 2.2.
 */
public record RequestTourBookingResult(
        String bookingId,
        String status
) {}
