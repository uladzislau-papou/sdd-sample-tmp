package com.dominikgaller.alpinebooking.guide.core.inport.result;

/**
 * Output carrier for UC12 – CancelTourByGuide.
 *
 * <p>{@code cancelledBookings} is reported back so the caller can see the cancellation's
 * reach. Zero is a valid success: a tour nobody had booked, or one whose bookings were
 * already cancelled or completed.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}, section 3.
 */
public record CancelTourByGuideResult(String status, int cancelledBookings) {
}
