package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.shared.domain.TourId;

import java.util.List;
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

    /**
     * Loads all {@link TourBooking} aggregates associated with a given tour, regardless
     * of status.
     *
     * <p><b>No production caller.</b> {@code TourStartedListener} previously used this and
     * filtered by status itself, which put a business rule in an inbound adapter
     * (architecture.definition.md section 4.8); it now uses
     * {@link #findConfirmedByTourId}. Retained pending a genuine caller — see
     * {@code documentation/ports/tour-booking-repository.outport.spec.md} Known Gaps.
     *
     * @param tourId the tour reference; must not be null
     * @return all bookings for the given tour, or an empty list if none exist
     */
    List<TourBooking> findByTourId(TourId tourId);

    /**
     * Returns the bookings for a tour that are eligible for activation, i.e. CONFIRMED.
     *
     * <p>Exists so the UC06 activation fan-out does not have to filter by status in an
     * inbound adapter — {@code architecture.definition.md} section 4.8 forbids business
     * logic in a listener. The aggregate still enforces the transition guard; this query
     * only selects candidates.
     *
     * @return possibly empty list, never null
     */
    List<TourBooking> findConfirmedByTourId(TourId tourId);
}
