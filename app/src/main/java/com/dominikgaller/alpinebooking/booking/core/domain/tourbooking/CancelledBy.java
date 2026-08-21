package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

/**
 * Who initiated a cancellation (UC08, UC09).
 *
 * <p>The distinction is the whole point of making cancellation attributable: before it
 * existed, {@code TourBookingCancelled} recorded *that* a booking was cancelled but not by whom,
 * leaving participant- and guide-initiated cancellations indistinguishable downstream.
 *
 * <p>An enum rather than a value object: the set is closed, there is nothing to validate,
 * and no behaviour attaches to a member.
 *
 * <p>SDD: See {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md} and
 * {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}.
 */
public enum CancelledBy {

    /** The participant cancelled their own booking (UC08). */
    USER,

    /** The guide cancelled the tour, cascading to its bookings (UC09, UC12). */
    GUIDE
}
