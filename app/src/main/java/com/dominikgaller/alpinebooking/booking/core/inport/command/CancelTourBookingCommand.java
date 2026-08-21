package com.dominikgaller.alpinebooking.booking.core.inport.command;

/**
 * Command carrying the input data for the CancelTourBooking use case (UC03, extended by UC08).
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 * {@code reason} is optional free text and may be null; it is validated into a
 * {@code CancellationReason} by the driver rather than here, because a command is a
 * carrier, not a guard, and validation belongs to the domain type
 * ({@code modelling.definition.md}).
 *
 * <p>No {@code cancelledAt}: this inport is reached only from REST, and a REST driver takes
 * the time from {@code ClockPort} ({@code architecture.definition.md} § 8.1). UC09's
 * separate inport does carry one, because there the timestamp is a fact relayed from the
 * {@code guide} context rather than a client's assertion.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancel-tour-booking.spec.md}, section 2,
 * and {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}, section 2.
 */
public record CancelTourBookingCommand(
        String bookingId,
        String reason
) {
}
