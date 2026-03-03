package com.dominikgaller.alpinebooking.booking.inbound.rest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * REST request body for UC01 – RequestTourBooking.
 *
 * <p>Bean Validation constraints enforce syntactic rules at the HTTP boundary.
 * Semantic rules (future date, capacity) are enforced by the domain.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}, section 2.
 */
public record RequestTourBookingRequest(
        @NotBlank String tourId,
        @NotNull LocalDate tourDate,
        @Min(1) int participantCount,
        @NotBlank String contactName,
        @NotBlank String contactEmail
) {
}
