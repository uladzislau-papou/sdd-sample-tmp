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

    /**
     * Returns the bookings for a tour that are eligible for completion, i.e. ACTIVE.
     *
     * <p>Mirrors {@link #findConfirmedByTourId} for UC07: the status criterion lives in the
     * query so the {@code TourCompletedListener} holds no business logic
     * ({@code architecture.definition.md} section 4.8, section 4.6 "Selection criteria in
     * write-side queries"). The aggregate still enforces the transition guard.
     *
     * @return possibly empty list, never null
     */
    List<TourBooking> findActiveByTourId(TourId tourId);

    /**
     * All bookings for a tour that a guide may still cancel — every non-terminal state
     * ({@code REQUESTED}, {@code CONFIRMED}, {@code ACTIVE}).
     *
     * <p>The "which bookings are affected" criterion lives here rather than in the caller,
     * for the same reason {@link #findActiveByTourId} does: it is business knowledge that
     * belongs to this context ({@code architecture.definition.md} § 4.6). It matters more
     * here than anywhere else, because the caller is the {@code guide} context — if the
     * criterion lived there, {@code guide} would have to know {@code TourBooking}'s state
     * model, and the boundary would be gone.
     *
     * <p>Already-{@code CANCELLED} and {@code COMPLETED} bookings are excluded by the query,
     * so the fan-out never asks the aggregate to do something it would reject. The
     * aggregate's guard still holds independently.
     *
     * @param tourId the tour whose bookings should be cancelled; must not be null
     * @return the matching aggregates, empty if none
     */
    List<TourBooking> findCancellableByTourId(TourId tourId);
}
