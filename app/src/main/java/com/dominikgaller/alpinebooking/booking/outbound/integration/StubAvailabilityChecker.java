package com.dominikgaller.alpinebooking.booking.outbound.integration;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityChecker;

/**
 * Stub implementation of {@link AvailabilityChecker}.
 *
 * <p>Always returns {@link Integer#MAX_VALUE} available spots, simulating unlimited availability.
 * Intended for local development and early integration testing only.
 *
 * <p>SDD: See {@code documentation/ports/availability-checker.outport.spec.md}.
 */
public class StubAvailabilityChecker implements AvailabilityChecker {

    @Override
    public AvailableCapacity checkAvailability(final TourId tourId, final TourDate tourDate) {
        return new AvailableCapacity(Integer.MAX_VALUE);
    }
}
