package com.dominikgaller.alpinebooking.booking.inbound.rest.response;

/**
 * Response DTO returned by the UC03 – CancelTourBooking endpoint.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancel-tour-booking.spec.md}, section 3.
 */
public record CancelTourBookingResponse(String status) {
}
