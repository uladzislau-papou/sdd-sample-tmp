package com.dominikgaller.alpinebooking.booking.core.inport;

import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityUnavailableException;

/**
 * Inbound port for the UC01 – RequestTourBooking use case.
 *
 * <p>The only entry point for creating a new tour booking from the outside world.
 * Framework-free: no Spring, no Jakarta annotations.
 *
 * <p>The transaction boundary is owned by the driver implementation.
 * Callers (e.g., REST controllers) must not wrap this call in their own transaction.
 *
 * <p>SDD: See {@code documentation/ports/request-tour-booking.inport.spec.md}.
 */
public interface RequestTourBookingUseCase {

    /**
     * Requests a new tour booking.
     *
     * @param command the booking input data; must not be null
     * @return the result containing the new booking ID and status
     * @throws InvalidBookingRequestException if the tour date is in the past or participant count < 1
     * @throws CapacityExceededException      if participant count exceeds available capacity
     * @throws AvailabilityUnavailableException if the availability check infrastructure fails
     */
    RequestTourBookingResult request(RequestTourBookingCommand command);
}
