package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Result returned by the MarkBookingActive use case.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}, section 3.
 */
public record MarkBookingActiveResult(String status) {
}
