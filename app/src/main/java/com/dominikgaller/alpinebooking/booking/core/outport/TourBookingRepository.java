package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;

import java.util.Optional;

/**
 * Outbound port for persisting the {@link TourBooking} aggregate (write side).
 *
 * <p>The implementation must persist each operation atomically.
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

    /**
     * Loads a {@link TourBooking} aggregate by its identity.
     *
     * @param bookingId the booking identity; must not be null
     * @return the aggregate if found, or {@link Optional#empty()} otherwise
     */
    Optional<TourBooking> findById(BookingId bookingId);

    /**
     * Persists a state change on an existing {@link TourBooking} aggregate.
     *
     * @param booking the aggregate whose current state should be written; must not be null
     */
    void update(TourBooking booking);
}
