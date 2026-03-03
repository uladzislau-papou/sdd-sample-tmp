package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;

/**
 * Outbound port for querying available capacity for a given tour and date.
 *
 * <p>This port provides the data needed by the domain to enforce the capacity invariant.
 * It does NOT enforce the invariant itself — that is the responsibility of {@code TourBooking}.
 *
 * <p>Framework-free: implementations live in {@code outbound.integration}.
 *
 * <p>SDD: See {@code documentation/ports/availability-checker.outport.spec.md}.
 */
public interface AvailabilityChecker {

    /**
     * Returns the number of available spots for the given tour and date.
     *
     * <p>The returned value is a snapshot; no reservation is made by this call.
     *
     * @param tourId   the tour to check; must not be null
     * @param tourDate the date to check; must not be null
     * @return the available capacity at time of call
     * @throws AvailabilityUnavailableException if the availability source is unreachable
     */
    AvailableCapacity checkAvailability(TourId tourId, TourDate tourDate);
}
