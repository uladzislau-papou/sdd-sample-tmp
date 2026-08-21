package com.dominikgaller.alpinebooking.guide.core.inport.command;

import java.time.Instant;
import java.util.Optional;

/**
 * Input carrier for UC11 – CompleteTour.
 *
 * <p>{@code completedAt} empty means "now", resolved from {@code ClockPort} by the driver.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}, section 2.
 */
public record CompleteTourCommand(String guideTourId, Optional<Instant> completedAt) {
}
