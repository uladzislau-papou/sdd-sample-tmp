package com.dominikgaller.alpinebooking.booking.core.inport.result;

/**
 * Output carrier for UC09 – MarkBookingCancelledByGuide.
 *
 * <p>Reports how many bookings were cancelled, so the calling context can confirm the
 * cancellation took effect before reporting its own. That confirmation is the reason UC09 is
 * a synchronous call rather than an event, unlike UC06 and UC07.
 *
 * <p>Zero is a valid, successful outcome: a tour with no bookings, or one whose bookings
 * were all already cancelled or completed. The guide still cancelled the tour.
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}, section 3.
 */
public record MarkBookingCancelledByGuideResult(int cancelledCount) {
}
