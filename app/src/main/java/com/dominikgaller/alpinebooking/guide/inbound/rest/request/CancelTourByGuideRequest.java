package com.dominikgaller.alpinebooking.guide.inbound.rest.request;

/**
 * REST request body for UC12 – CancelTourByGuide.
 *
 * <p>Optional, as is the field itself: a bare {@code POST /guide-tours/{id}/cancel} cancels
 * the tour without recording a reason.
 *
 * <p>Carries no {@code cancelledAt}. The driver reads the cancellation time from
 * {@code ClockPort} ({@code architecture.definition.md} § 8.1) — the time an action happened
 * is the system's observation, not the caller's claim.
 *
 * <p>No Bean Validation annotation on {@code reason}, matching
 * {@code CancelTourBookingRequest} on the booking side: the length rule is a business rule
 * {@code guide.core.domain.guidetour.CancellationReason} enforces, so it holds for every
 * caller rather than only the HTTP one.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}, section 9.
 */
public record CancelTourByGuideRequest(String reason) {
}
