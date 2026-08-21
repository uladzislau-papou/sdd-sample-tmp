package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancelledBy;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCancelledByGuideCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCancelledByGuideResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCancelledByGuideUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service (driver) implementing UC09 – MarkBookingCancelledByGuide.
 *
 * <p>Reached synchronously from {@code guide}'s {@code CancelTourByGuideDriver}, not over
 * HTTP. {@code @Transactional} here joins the caller's transaction rather than opening its
 * own — the propagation default is {@code REQUIRED} — which is what makes a failure roll the
 * tour cancellation back. Contrast {@code TourCompletedListener}, which deliberately uses
 * {@code REQUIRES_NEW} because completion is a notification the booking side may handle
 * independently.
 *
 * <p>Idempotency is the aggregate's decision, not this driver's: {@code cancel} returns
 * early for an already-cancelled booking when the caller is a guide. This driver only
 * observes whether an event resulted and skips the write and the publish if none did — the
 * same shape as {@code MarkBookingCompletedDriver}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}.
 */
@Service
@Transactional
public class MarkBookingCancelledByGuideDriver implements MarkBookingCancelledByGuideUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public MarkBookingCancelledByGuideDriver(
            final TourBookingRepository tourBookingRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.tourBookingRepository = tourBookingRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public MarkBookingCancelledByGuideResult cancelByGuide(
            final MarkBookingCancelledByGuideCommand command) {

        final TourId tourId = new TourId(command.tourId());

        // Validated before the query, so a malformed reason fails without touching the
        // database and regardless of how many bookings the tour has.
        final CancellationReason reason = command.reason() == null
                ? null
                : new CancellationReason(command.reason());

        // The caller's timestamp wins: the guide already recorded when it cancelled the
        // tour, and re-dating it here would leave the two contexts disagreeing about one
        // moment by the duration of this call (architecture.definition.md section 8.1).
        final Instant cancelledAt =
                Optional.ofNullable(command.cancelledAt()).orElseGet(clockPort::now);

        // Which bookings are affected is decided by the query, not here — the caller is
        // another bounded context and must not know this aggregate's state model.
        final List<TourBooking> cancellable =
                tourBookingRepository.findCancellableByTourId(tourId);

        int cancelled = 0;
        for (final TourBooking booking : cancellable) {
            booking.cancel(cancelledAt, CancelledBy.GUIDE, reason, command.guideTourId());

            final var events = booking.pullDomainEvents();
            if (!events.isEmpty()) {
                tourBookingRepository.update(booking);
                events.forEach(domainEventPublisher::publish);
                cancelled++;
            }
        }

        return new MarkBookingCancelledByGuideResult(cancelled);
    }
}
