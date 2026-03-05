package com.dominikgaller.alpinebooking.guide.inbound.rest.request;

import java.time.Instant;

/**
 * REST request body for UC05 – StartTour.
 *
 * <p>{@code startedAt} is optional. If absent, the application service defaults to
 * the current time via {@code ClockPort}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 2.
 */
public record StartTourRequest(Instant startedAt) {
}
