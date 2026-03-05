package com.dominikgaller.alpinebooking.booking.core.inport.usecase;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingActiveCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingActiveResult;

/**
 * Inbound port for the UC06 – MarkBookingActive use case.
 *
 * <p>Transitions an existing booking from CONFIRMED to ACTIVE.
 * If the booking is already ACTIVE the call is a no-op (idempotent).
 * Framework-free: no Spring, no Jakarta annotations.
 *
 * <p>The transaction boundary is owned by the driver implementation.
 * Callers (e.g., REST controllers) must not wrap this call in their own transaction.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}.
 */
public interface MarkBookingActiveUseCase {

    /**
     * Marks an existing tour booking as active.
     *
     * @param command the command carrying the booking ID and optional startedAt / guideTourId; must not be null
     * @return the result containing the updated status
     * @throws BookingNotFoundException     if no booking with the given ID exists
     * @throws InvalidBookingStateException if the booking is in a state other than CONFIRMED or ACTIVE
     */
    MarkBookingActiveResult markActive(MarkBookingActiveCommand command);
}
