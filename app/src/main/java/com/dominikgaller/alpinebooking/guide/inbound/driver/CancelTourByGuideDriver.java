package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCancelledByGuideCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCancelledByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.CancellationReason;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CancelTourByGuideResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CancelTourByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service (driver) implementing UC12 – CancelTourByGuide.
 *
 * <p><b>This is the only class in the codebase that crosses a bounded-context boundary.</b>
 * It cancels the {@link GuideTour}, then calls {@code booking}'s
 * {@link MarkBookingCancelledByGuideUseCase} — that context's published API — inside the same
 * transaction. {@code architecture.definition.md} § 11 rule 3 permits a driver, and only a
 * driver, to depend on another context's {@code core.inport}; § 10 "One transaction spanning
 * two bounded contexts" states the four conditions under which sharing the transaction is
 * allowed, all of which this use case meets.
 *
 * <p>It imports exactly two types from {@code booking}, both from {@code core.inport}. It
 * knows nothing of {@code TourBooking}, its states, or which bookings a tour has — it passes
 * a {@code tourId} and {@code booking} decides. That is deliberate and enforced:
 * {@code ContextRegistryTest.guide_doesNotImportBookingInternals} fails if this file reaches
 * past the inport.
 *
 * <p>No outport. An earlier design gave this context a {@code BookingCancellationPort} for
 * {@code booking} to implement; ADR-0008 rejected it, because the single implementation would
 * have delegated to the very inport called here, relocating the coupling behind an interface
 * nobody could substitute.
 *
 * <p>Ordering matters. The tour is cancelled and persisted <em>before</em> the booking side is
 * called, so the aggregate's own guard rejects a FINISHED or already-CANCELLED tour before any
 * cross-context work happens (AC-06). Events are drained and published last, after both sides
 * have succeeded — {@code DomainEventPublisher} defers to after commit (ADR-0002), so a
 * rollback cannot leak a {@code TourCancelledByGuide} for a tour that is still scheduled.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}.
 */
@Service
@Transactional
public class CancelTourByGuideDriver implements CancelTourByGuideUseCase {

    private final GuideTourRepository guideTourRepository;
    private final MarkBookingCancelledByGuideUseCase markBookingCancelledByGuide;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public CancelTourByGuideDriver(
            final GuideTourRepository guideTourRepository,
            final MarkBookingCancelledByGuideUseCase markBookingCancelledByGuide,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.guideTourRepository = guideTourRepository;
        this.markBookingCancelledByGuide = markBookingCancelledByGuide;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public CancelTourByGuideResult cancel(final CancelTourByGuideCommand command) {
        final GuideTourId guideTourId =
                new GuideTourId(UUID.fromString(command.guideTourId()));
        // Validated by the value object, not here: this context stores the reason, so the
        // length is its invariant and belongs in the domain (section 6.2 clause A). An
        // earlier revision validated inline and threw IllegalArgumentException, which let
        // GuideTour.cancel accept a reason no UPDATE would take.
        final CancellationReason reason = command.reason() == null
                ? null
                : new CancellationReason(command.reason());

        // Read from the clock, never the request: this driver sits behind REST (section 8.1).
        // The same instant is handed to the booking side below, so both contexts record one
        // moment rather than two separated by the duration of the call.
        final Instant cancelledAt = clockPort.now();

        final GuideTour guideTour = guideTourRepository.findById(guideTourId)
                .orElseThrow(() -> new GuideTourNotFoundException(command.guideTourId()));

        // Before the cross-context call, so an uncancellable tour is rejected without
        // touching the other context at all (AC-06).
        guideTour.cancel(cancelledAt, reason);
        guideTourRepository.update(guideTour);

        final int cancelledBookings = cancelBookings(guideTour, cancelledAt, reason);

        guideTour.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new CancelTourByGuideResult(guideTour.status().name(), cancelledBookings);
    }

    /**
     * The cross-context call. Any failure becomes a
     * {@link BookingCancellationFailedException}, which surfaces as a 502 and — because this
     * method runs inside the caller's transaction — rolls the tour cancellation back with it.
     *
     * <p>Catching {@link RuntimeException} broadly is deliberate here, and is the one place in
     * this codebase where it is. From this side, every way the booking context can fail is the
     * same event: it did not complete, so the tour must not be reported cancelled. Catching
     * narrower types would mean {@code guide} enumerating {@code booking}'s exception classes,
     * which is precisely the coupling the inport exists to prevent — and any type not listed
     * would escape as a 500 rather than the 502 the contract promises.
     */
    private int cancelBookings(
            final GuideTour guideTour,
            final Instant cancelledAt,
            final CancellationReason reason) {
        try {
            return markBookingCancelledByGuide.cancelByGuide(
                    new MarkBookingCancelledByGuideCommand(
                            guideTour.tourId().value(),
                            cancelledAt,
                            guideTour.id().value().toString(),
                            reason == null ? null : reason.value()))
                    .cancelledCount();
        } catch (RuntimeException cause) {
            throw new BookingCancellationFailedException(guideTour.tourId().value(), cause);
        }
    }

}
