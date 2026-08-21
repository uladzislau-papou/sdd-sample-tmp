package com.dominikgaller.alpinebooking.booking.core.inport.command;

import java.time.Instant;

/**
 * Input carrier for UC07 – MarkBookingCompleted.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 * {@code completedAt} may be null, in which case the driver resolves it from
 * {@code ClockPort}. {@code guideTourId} is an optional correlation identifier linking to
 * the guide tour execution. The same three-component shape as
 * {@code MarkBookingActiveCommand}, deliberately: the two use cases mirror each other and
 * an asymmetry here would make only half the booking lifecycle traceable.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}, section 2.
 */
public record MarkBookingCompletedCommand(
        String bookingId,
        Instant completedAt,
        String guideTourId
) {
}
