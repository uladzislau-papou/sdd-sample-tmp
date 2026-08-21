package com.dominikgaller.alpinebooking.guide.inbound.rest.response;

/**
 * REST response body for UC11 – CompleteTour. Always {@code "FINISHED"} on success.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 9.
 */
public record CompleteTourResponse(String status) {
}
