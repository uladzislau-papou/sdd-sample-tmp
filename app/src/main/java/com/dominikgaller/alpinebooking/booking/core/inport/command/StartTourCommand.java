package com.dominikgaller.alpinebooking.booking.core.inport.command;

import java.time.Instant;
import java.util.Optional;

/**
 * Input data carrier for the UC05 – StartTour use case.
 *
 * <p>If {@code startedAt} is absent, the application service resolves it
 * to the current time via {@code ClockPort}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 2.
 */
public record StartTourCommand(String guideTourId, Optional<Instant> startedAt) {
}
