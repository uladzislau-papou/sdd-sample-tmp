package com.dominikgaller.alpinebooking.booking.inbound.listener;

import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCompletedCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCompletedUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCompleted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link TourCompleted} published by the {@code guide} bounded context (UC11) and
 * transitions the ACTIVE bookings for that tour to COMPLETED.
 *
 * <p>Runs in a new transaction after the guide tour's transaction commits (ADR 0002), so the
 * completion is durable regardless of the originating context's outcome.
 *
 * <p>Holds no business logic: the "which bookings are eligible" criterion lives in
 * {@code findActiveByTourId} and the transition guard lives on the aggregate
 * ({@code architecture.definition.md} section 4.8). Mirrors {@link TourStartedListener}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}.
 */
@Component
public class TourCompletedListener {

    private final TourBookingRepository tourBookingRepository;
    private final MarkBookingCompletedUseCase markBookingCompletedUseCase;

    public TourCompletedListener(
            final TourBookingRepository tourBookingRepository,
            final MarkBookingCompletedUseCase markBookingCompletedUseCase) {
        this.tourBookingRepository = tourBookingRepository;
        this.markBookingCompletedUseCase = markBookingCompletedUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTourCompleted(final TourCompleted event) {
        tourBookingRepository.findActiveByTourId(event.tourId())
                .forEach(b -> markBookingCompletedUseCase.markCompleted(
                        new MarkBookingCompletedCommand(
                                b.bookingId().value().toString(),
                                event.completedAt(),
                                event.guideTourId())));
    }
}
