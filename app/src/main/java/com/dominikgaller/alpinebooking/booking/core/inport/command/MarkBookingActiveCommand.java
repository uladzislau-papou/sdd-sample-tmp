package com.dominikgaller.alpinebooking.booking.core.inport.command;

import java.time.Instant;

/**
 * Command carrying the input data for the MarkBookingActive use case.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 * {@code startedAt} is optional; when null the driver resolves the current time via ClockPort.
 * {@code guideTourId} is an optional correlation identifier linking to the guide tour execution.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}, section 2.
 */
public record MarkBookingActiveCommand(
        String bookingId,
        Instant startedAt,
        String guideTourId
) {
}
