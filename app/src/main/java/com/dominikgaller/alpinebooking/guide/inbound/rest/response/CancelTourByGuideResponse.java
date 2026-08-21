package com.dominikgaller.alpinebooking.guide.inbound.rest.response;

/**
 * REST response body for UC12 – CancelTourByGuide.
 *
 * <p>{@code cancelledBookings} reports the cancellation's reach, so a guide can see that the
 * bookings went with the tour rather than having to check separately. Zero is a valid
 * success.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}, section 9.
 */
public record CancelTourByGuideResponse(String status, int cancelledBookings) {
}
