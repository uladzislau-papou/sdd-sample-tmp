package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Result returned by the ConfirmTourBooking use case.
 *
 * <p>Contains only standard-library types — no domain value objects.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 3.
 */
public record ConfirmTourBookingResult(String status) {
}
