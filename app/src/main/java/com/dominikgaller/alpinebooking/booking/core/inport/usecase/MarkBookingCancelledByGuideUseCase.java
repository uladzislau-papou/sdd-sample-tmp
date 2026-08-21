package com.dominikgaller.alpinebooking.booking.core.inport.usecase;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCancelledByGuideCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCancelledByGuideResult;

/**
 * Inbound port for UC09 – MarkBookingCancelledByGuide.
 *
 * <p>This interface <b>is</b> the cross-context contract. The {@code guide} context calls it
 * synchronously from {@code CancelTourByGuideDriver}, inside that driver's transaction, so a
 * failure here rolls back the tour cancellation. `architecture.definition.md` § 11 rule 3
 * permits a driver to depend on another context's {@code core.inport} — its published API.
 *
 * <p>No outport is involved. An earlier design gave {@code guide} a
 * {@code BookingCancellationPort}; it was rejected because its single implementation would
 * have delegated to this very interface, relocating the coupling rather than removing it.
 * See {@code documentation/adr/0008-synchronous-cross-context-cancellation.adr.md}.
 *
 * <p>Has no REST surface.
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}.
 */
public interface MarkBookingCancelledByGuideUseCase {

    /**
     * Cancels every booking for a tour that the guide has cancelled.
     *
     * <p>Takes a <b>tour</b>, not a booking. Which bookings are affected is this context's
     * business — resolved by {@code findCancellableByTourId}, which selects every
     * non-terminal state — so the caller never learns {@code TourBooking}'s state model.
     *
     * <p>Idempotent and total: bookings already {@code CANCELLED} or {@code COMPLETED} are
     * excluded by the query and therefore not touched, existing attribution is preserved,
     * and a tour with nothing left to cancel returns zero rather than failing. That matters
     * because the caller runs this inside its own transaction — anything that threw here
     * would roll back the tour cancellation.
     *
     * @param command the tour to cancel bookings for, plus the guide's cancellation time,
     *                correlation id and reason. A null {@code cancelledAt} is resolved from
     *                {@code ClockPort}
     * @return how many bookings were cancelled; zero is a successful outcome
     * @throws IllegalArgumentException       if {@code tourId} is null or blank
     * @throws InvalidBookingRequestException if {@code reason} is blank or over 400 characters
     */
    MarkBookingCancelledByGuideResult cancelByGuide(MarkBookingCancelledByGuideCommand command);
}
