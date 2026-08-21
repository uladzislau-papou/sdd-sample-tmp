package com.dominikgaller.alpinebooking.guide.core.inport.usecase;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CompleteTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CompleteTourResult;

/**
 * Inbound port for UC11 – CompleteTour.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}.
 *
 * @throws GuideTourNotFoundException        if no guide tour exists for the id
 * @throws InvalidGuideTourStateException    if the tour is not RUNNING
 * @throws TourCompletedBeforeStartException if completion precedes the start
 */
public interface CompleteTourUseCase {

    CompleteTourResult complete(CompleteTourCommand command);
}
