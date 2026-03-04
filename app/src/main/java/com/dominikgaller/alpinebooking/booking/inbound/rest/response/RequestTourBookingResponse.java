package com.dominikgaller.alpinebooking.booking.inbound.rest.response;

/**
 * REST response body for UC01 – RequestTourBooking.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}, section 3.
 */
public record RequestTourBookingResponse(
        String bookingId,
        String status
) {
}
