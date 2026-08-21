package com.dominikgaller.alpinebooking.booking.core.inport.command;

import java.time.Instant;

/**
 * Input carrier for UC09 – MarkBookingCancelledByGuide.
 *
 * <p>Contains only primitive and standard-library types — no domain value objects.
 *
 * <p><b>Identifies a tour, not a booking.</b> The caller is the {@code guide} context, which
 * knows which tour it cancelled and must not know which bookings exist for it. Naming a
 * {@code bookingId} here — as an earlier revision did — would have forced {@code guide} to
 * enumerate bookings first, which means knowing {@code TourBooking}'s state model and having
 * a query surface into this context. The set of affected bookings is this context's
 * business, resolved by {@code TourBookingRepository.findCancellableByTourId}.
 *
 * <p>{@code tourId} is the shared {@code TourId} value as a plain {@code String} — the
 * identity is owned by no single context (ADR-0005 category 3), and commands carry
 * primitives regardless.
 *
 * <p>{@code cancelledAt} may be null, in which case the driver resolves it from
 * {@code ClockPort}. Unlike {@code CancelTourBookingCommand}, this one <b>does</b> carry a
 * timestamp: it arrives from the {@code guide} context, which already recorded when it
 * cancelled the tour, and re-dating it here would make the booking and the tour disagree
 * about one moment ({@code architecture.definition.md} § 8.1).
 *
 * <p>{@code guideTourId} and {@code reason} are optional. {@code reason} is validated into a
 * {@code CancellationReason} by the driver, not here — a command is a carrier, not a guard.
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}, section 2.
 */
public record MarkBookingCancelledByGuideCommand(
        String tourId,
        Instant cancelledAt,
        String guideTourId,
        String reason
) {
}
