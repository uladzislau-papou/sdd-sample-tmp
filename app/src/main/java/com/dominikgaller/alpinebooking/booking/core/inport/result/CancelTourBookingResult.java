package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Result returned by the CancelTourBooking use case.
 *
 * <p>Contains only standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancle-tour-booking.spec.md}, section 3.
 */
public record CancelTourBookingResult(String status) {
}
