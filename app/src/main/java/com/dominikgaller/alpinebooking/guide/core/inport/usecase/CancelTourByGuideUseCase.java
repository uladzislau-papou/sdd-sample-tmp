package com.dominikgaller.alpinebooking.guide.core.inport.usecase;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CancelTourByGuideResult;

/**
 * Inbound port for UC12 – CancelTourByGuide.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}.
 */
public interface CancelTourByGuideUseCase {

    /**
     * Cancels a scheduled or running guide tour and every cancellable booking attached to it.
     *
     * <p>The two happen in <b>one transaction</b>: if the booking side fails, the tour
     * cancellation does not commit. That is the point of the use case — a tour reported
     * cancelled while its bookings still believe it is going ahead is the failure it exists
     * to prevent ({@code architecture.definition.md} § 10, "One transaction spanning two
     * bounded contexts").
     *
     * @param command the tour to cancel and an optional reason
     * @return the resulting status and how many bookings were cancelled
     * @throws IllegalArgumentException           if {@code guideTourId} is not a well-formed UUID
     * @throws GuideTourNotFoundException         if no guide tour exists for the id
     * @throws InvalidGuideTourStateException     if the tour is {@code FINISHED} or already
     *                                            {@code CANCELLED}
     * @throws BookingCancellationFailedException if the booking side did not complete; the
     *                                            whole transaction is rolled back
     */
    CancelTourByGuideResult cancel(CancelTourByGuideCommand command);
}
