package com.dominikgaller.alpinebooking.booking.core.inport;

/**
 * Command carrying the input data for the CancelTourBooking use case.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancle-tour-booking.spec.md}, section 2.
 */
public record CancelTourBookingCommand(String bookingId) {
}
