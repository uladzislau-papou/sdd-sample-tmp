package com.dominikgaller.alpinebooking.booking.core.inport.command;

import java.time.Instant;

/**
 * Command carrying the input data for the CancelTourBooking use case (UC03, extended by UC08).
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 * {@code cancelledAt} is optional; when null the driver resolves the current time via
 * {@code ClockPort}. {@code reason} is optional free text, validated into a
 * {@code CancellationReason} by the driver rather than here: a command is a carrier, not a
 * guard, and validation belongs to the domain type ({@code modelling.definition.md}).
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancel-tour-booking.spec.md}, section 2,
 * and {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}, section 2.
 */
public record CancelTourBookingCommand(
        String bookingId,
        Instant cancelledAt,
        String reason
) {
}
