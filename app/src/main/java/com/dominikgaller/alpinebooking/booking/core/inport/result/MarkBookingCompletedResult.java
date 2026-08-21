package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Output carrier for UC07 – MarkBookingCompleted.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}, section 3.
 */
public record MarkBookingCompletedResult(String status) {
}
