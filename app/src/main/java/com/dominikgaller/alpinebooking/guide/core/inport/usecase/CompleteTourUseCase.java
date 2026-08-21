package com.dominikgaller.alpinebooking.guide.core.inport.usecase;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CompleteTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CompleteTourResult;

/**
 * Inbound port for UC11 – CompleteTour.
 *
 * <p>Implementations own the transaction boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}.
 */
public interface CompleteTourUseCase {

    /**
     * Completes a running guide tour.
     *
     * @param command the guide tour id and an optional completion time; when absent the
     *                implementation resolves it from {@code ClockPort}
     * @return the resulting status, always {@code "FINISHED"} on success
     * @throws GuideTourNotFoundException        if no guide tour exists for the id
     * @throws InvalidGuideTourStateException    if the tour is not {@code RUNNING}
     * @throws TourCompletedBeforeStartException if completion precedes the recorded start
     */
    CompleteTourResult complete(CompleteTourCommand command);
}
