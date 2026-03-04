package com.dominikgaller.alpinebooking.booking.core.inport;

import com.dominikgaller.alpinebooking.booking.core.domain.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityUnavailableException;

/**
 * Inbound port for the UC04 – ChangeParticipants use case.
 *
 * <p>Updates the participant count on an existing booking while respecting
 * the current available capacity for the tour. Only bookings in REQUESTED or
 * CONFIRMED state may be changed.
 * Framework-free: no Spring, no Jakarta annotations.
 *
 * <p>The transaction boundary is owned by the driver implementation.
 * Callers (e.g., REST controllers) must not wrap this call in their own transaction.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}.
 */
public interface ChangeParticipantsUseCase {

    /**
     * Changes the participant count of an existing tour booking.
     *
     * @param command the command carrying the booking ID and new participant count; must not be null
     * @return the result containing the updated participant count
     * @throws BookingNotFoundException       if no booking with the given ID exists
     * @throws InvalidBookingStateException   if the booking is not in REQUESTED or CONFIRMED state
     * @throws CapacityExceededException      if the new count exceeds available capacity
     * @throws AvailabilityUnavailableException if the availability system cannot be reached
     */
    ChangeParticipantsResult change(ChangeParticipantsCommand command);
}
