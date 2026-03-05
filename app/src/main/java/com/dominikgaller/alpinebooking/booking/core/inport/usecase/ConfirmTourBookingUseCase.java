package com.dominikgaller.alpinebooking.booking.core.inport.usecase;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ConfirmTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ConfirmTourBookingResult;

/**
 * Inbound port for the UC02 – ConfirmTourBooking use case.
 *
 * <p>Transitions an existing booking from REQUESTED to CONFIRMED.
 * Framework-free: no Spring, no Jakarta annotations.
 *
 * <p>The transaction boundary is owned by the driver implementation.
 * Callers (e.g., REST controllers) must not wrap this call in their own transaction.
 *
 * <p>SDD: See {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}.
 */
public interface ConfirmTourBookingUseCase {

    /**
     * Confirms an existing tour booking.
     *
     * @param command the command carrying the booking ID; must not be null
     * @return the result containing the updated status
     * @throws BookingNotFoundException     if no booking with the given ID exists
     * @throws InvalidBookingStateException if the booking is not in REQUESTED state
     */
    ConfirmTourBookingResult confirm(ConfirmTourBookingCommand command);
}
