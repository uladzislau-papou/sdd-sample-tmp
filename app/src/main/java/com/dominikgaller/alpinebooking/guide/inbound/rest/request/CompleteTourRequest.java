package com.dominikgaller.alpinebooking.guide.inbound.rest.request;

import java.time.Instant;

/**
 * REST request body for UC11 – CompleteTour. Optional: an absent body, or an absent
 * {@code completedAt}, means "now" and is resolved from {@code ClockPort} by the driver.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 9.
 */
public record CompleteTourRequest(Instant completedAt) {
}
