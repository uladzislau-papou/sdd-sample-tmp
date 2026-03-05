package com.dominikgaller.alpinebooking.booking.inbound.rest.response;

/**
 * Response DTO returned by the UC05 – StartTour endpoint.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 3.
 */
public record StartTourResponse(String status) {
}
