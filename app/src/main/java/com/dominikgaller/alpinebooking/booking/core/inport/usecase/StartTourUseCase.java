package com.dominikgaller.alpinebooking.booking.core.inport.usecase;

import com.dominikgaller.alpinebooking.booking.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.StartTourResult;

/**
 * Inbound port for UC05 – StartTour.
 *
 * <p>Transitions a scheduled {@code GuideTour} to the {@code RUNNING} state and
 * publishes a {@code TourStarted} domain event.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
public interface StartTourUseCase {

    /**
     * Starts the guide tour identified by the given command.
     *
     * @param command the start command containing the guide tour id and optional start time
     * @return the result containing the new status
     */
    StartTourResult start(StartTourCommand command);
}
