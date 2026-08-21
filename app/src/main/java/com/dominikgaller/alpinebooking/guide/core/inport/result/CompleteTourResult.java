package com.dominikgaller.alpinebooking.guide.core.inport.result;

/**
 * Output carrier for UC11 – CompleteTour.
 *
 * <p>Carries the status as a {@code String}; {@code GuideTourStatus} is a domain type and
 * must not leak into an API contract ({@code architecture.definition.md} section 4.5).
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 3.
 */
public record CompleteTourResult(String status) {
}
