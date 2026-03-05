package com.dominikgaller.alpinebooking.booking.core.domain;

/**
 * Lifecycle states of a {@link GuideTour} aggregate.
 *
 * <p>Allowed transitions:
 * <ul>
 *   <li>SCHEDULED → RUNNING</li>
 *   <li>RUNNING → FINISHED</li>
 *   <li>SCHEDULED → CANCELLED</li>
 *   <li>RUNNING → CANCELLED</li>
 * </ul>
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
public enum GuideTourStatus {
    SCHEDULED,
    RUNNING,
    FINISHED,
    CANCELLED
}
