package com.dominikgaller.alpinebooking.booking.core.domain;

/**
 * Lifecycle states of a {@link TourBooking} aggregate.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>REQUESTED → CONFIRMED</li>
 *   <li>REQUESTED → CANCELLED</li>
 *   <li>CONFIRMED → CANCELLED</li>
 *   <li>CONFIRMED → ACTIVE</li>
 *   <li>ACTIVE → COMPLETED</li>
 * </ul>
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 3.
 */
public enum TourBookingStatus {
    REQUESTED,
    CONFIRMED,
    CANCELLED,
    ACTIVE,
    COMPLETED
}
