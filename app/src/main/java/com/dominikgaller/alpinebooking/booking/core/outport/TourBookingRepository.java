package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;

/**
 * Outbound port for persisting the {@link TourBooking} aggregate (write side).
 *
 * <p>The implementation must persist the aggregate atomically.
 * No update method is required for UC01.
 *
 * <p>Framework-free: implementations live in {@code outbound.persistence.write}.
 *
 * <p>SDD: See {@code documentation/ports/tour-booking-repository.outport.spec.md}.
 */
public interface TourBookingRepository {

    /**
     * Persists a new {@link TourBooking} aggregate.
     *
     * @param booking the aggregate to persist; must not be null
     */
    void save(TourBooking booking);
}
