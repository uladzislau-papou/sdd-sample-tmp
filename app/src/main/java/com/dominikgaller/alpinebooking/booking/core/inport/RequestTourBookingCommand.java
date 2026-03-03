package com.dominikgaller.alpinebooking.booking.core.inport;

import java.time.LocalDate;

/**
 * Command carrying the input data for the RequestTourBooking use case.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 * Structural validation (non-blank, future date, count >= 1) is enforced by the
 * domain layer when the driver maps this command to domain types.
 *
 * <p>SDD: See {@code documentation/ports/request-tour-booking.inport.spec.md}, section 2.1.
 */
public record RequestTourBookingCommand(
        String tourId,
        LocalDate tourDate,
        int participantCount,
        String contactName,
        String contactEmail
) {}
