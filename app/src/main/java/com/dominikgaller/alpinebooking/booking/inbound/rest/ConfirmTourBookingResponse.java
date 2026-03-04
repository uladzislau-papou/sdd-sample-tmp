package com.dominikgaller.alpinebooking.booking.inbound.rest;

/**
 * Response DTO returned by the UC02 – ConfirmTourBooking endpoint.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 3.
 */
public record ConfirmTourBookingResponse(String status) {
}
