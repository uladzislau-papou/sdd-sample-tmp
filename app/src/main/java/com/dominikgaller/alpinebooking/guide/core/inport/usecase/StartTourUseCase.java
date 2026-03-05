package com.dominikgaller.alpinebooking.guide.core.inport.usecase;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.StartTourResult;

/**
 * Inbound port for UC05 – StartTour.
 *
 * <p>Transitions a scheduled {@code GuideTour} to the {@code RUNNING} state and
 * publishes a {@code TourStarted} domain event.
 *
 * <p>The transaction boundary is owned by the driver implementation.
 * Callers (e.g., REST controllers) must not wrap this call in their own transaction.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
public interface StartTourUseCase {

    /**
     * Starts the guide tour identified by the given command.
     *
     * @param command the start command containing the guide tour id and optional start time
     * @return the result containing the new status
     * @throws GuideTourNotFoundException     if no guide tour with the given ID exists
     * @throws InvalidGuideTourStateException if the guide tour is not in SCHEDULED state
     * @throws TourStartTooEarlyException     if startedAt is before the scheduled start time
     */
    StartTourResult start(StartTourCommand command);
}
