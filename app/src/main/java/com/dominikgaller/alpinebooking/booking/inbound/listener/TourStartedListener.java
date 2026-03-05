package com.dominikgaller.alpinebooking.booking.inbound.listener;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingActiveCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingActiveUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.event.TourStarted;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link TourStarted} published by the {@code guide} bounded context and
 * transitions all CONFIRMED bookings for that tour to ACTIVE.
 *
 * <p>Runs in a new transaction after the guide tour's transaction commits (ADR 0002),
 * so the activation is durable regardless of the originating context's outcome.
 *
 * <p>This listener is the only valid trigger for the CONFIRMED → ACTIVE transition;
 * there is no public REST endpoint for direct activation.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}.
 */
@Component
public class TourStartedListener {

    private final TourBookingRepository tourBookingRepository;
    private final MarkBookingActiveUseCase markBookingActiveUseCase;

    public TourStartedListener(
            final TourBookingRepository tourBookingRepository,
            final MarkBookingActiveUseCase markBookingActiveUseCase) {
        this.tourBookingRepository = tourBookingRepository;
        this.markBookingActiveUseCase = markBookingActiveUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTourStarted(final TourStarted event) {
        tourBookingRepository.findByTourId(event.tourId()).stream()
                .filter(b -> b.status() == TourBookingStatus.CONFIRMED)
                .forEach(b -> markBookingActiveUseCase.markActive(
                        new MarkBookingActiveCommand(
                                b.bookingId().value().toString(),
                                event.startedAt(),
                                event.guideTourId())));
    }
}
