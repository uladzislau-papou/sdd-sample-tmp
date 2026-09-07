package com.example.service.booking.core.outport

import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.TourBooking
import com.example.service.shared.domain.TourId

/**
 * Outbound port for persisting the `TourBooking` aggregate.
 *
 * Each operation must be atomic.
 *
 * `update` MUST write **every** mutable field of the aggregate, not only the ones the
 * calling use case happens to change. A partial update once discarded an entire use
 * case's effect while its own tests stayed green, because each test only asserted the one
 * field it cared about (`documentation/ports/tour-booking-repository.outport.spec.md`
 * § 2.3). The rule is stated as a criterion rather than as a list of fields, because a
 * list goes stale the moment the aggregate gains one.
 *
 * SDD: see `documentation/ports/tour-booking-repository.outport.spec.md`.
 */
interface TourBookingRepository {
    fun save(booking: TourBooking)

    /** Returns the aggregate, or null when no booking has that identity. */
    fun findById(bookingId: BookingId): TourBooking?

    /**
     * Every confirmed booking for [tourId].
     *
     * UC06's fan-out: when a tour starts, these are the bookings that become active. The
     * filter is on the confirmed state specifically — a requested booking nobody confirmed
     * must not be carried along by the tour starting.
     */
    fun findConfirmedByTourId(tourId: TourId): List<TourBooking>

    fun update(booking: TourBooking)
}
