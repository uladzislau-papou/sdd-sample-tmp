package com.dominikgaller.alpinebooking.booking.core.inport.usecase;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCompletedCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCompletedResult;

/**
 * Inbound port for UC07 – MarkBookingCompleted.
 *
 * <p>Has no REST surface. Triggered by {@code TourCompleted} from the {@code guide} context
 * (UC11) via {@code TourCompletedListener}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}.
 */
public interface MarkBookingCompletedUseCase {

    /**
     * Marks a booking as completed after its tour has finished.
     *
     * <p>Idempotent: a booking already in {@code COMPLETED} state is a no-op — nothing is
     * persisted and no event is published.
     *
     * @param command the booking to complete and the completion time; a null
     *                {@code completedAt} is resolved from {@code ClockPort}
     * @return the resulting status, always {@code "COMPLETED"} on success
     * @throws IllegalArgumentException     if {@code bookingId} is not a well-formed UUID
     * @throws BookingNotFoundException     if no booking exists for the id
     * @throws InvalidBookingStateException if the booking is neither ACTIVE nor already COMPLETED
     */
    MarkBookingCompletedResult markCompleted(MarkBookingCompletedCommand command);
}
