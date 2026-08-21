package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCancelledByGuideCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCancelledByGuideResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCancelledByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.CancellationReason;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidCancellationReasonException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCancelledByGuide;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spring-free unit tests for {@link CancelTourByGuideDriver} (UC12).
 *
 * <p>The {@code booking} inport is an in-line stub, so these tests cover this driver's
 * orchestration: ordering, the reason ceiling, failure translation and event publication.
 *
 * <p>What they deliberately do <em>not</em> cover: that the shared transaction actually rolls
 * back. That is Spring propagation, not driver logic, and asserting it here would test the
 * framework — {@link CancelTourByGuideRollbackIT} does it against a real database.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}.
 */
class CancelTourByGuideDriverTest {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
    private static final TourId TOUR_REF = new TourId("TOUR-42");
    private static final String REASON = "Severe weather warning";

    private final GuideTourStubRepository repository = new GuideTourStubRepository();
    private final RecordingBookingCancellation bookingCancellation =
            new RecordingBookingCancellation();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final CancelTourByGuideDriver driver = new CancelTourByGuideDriver(
            repository, bookingCancellation, publisher, clock);

    // ── AC-01 / AC-02: happy paths ───────────────────────────────────────────

    @Test
    void cancel_fromScheduled_returnsCancelledStatus() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        assertThat(driver.cancel(command(tour)).status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_fromRunning_returnsCancelledStatus() {
        final GuideTour tour = preloadedTour(GuideTourStatus.RUNNING);

        assertThat(driver.cancel(command(tour)).status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_persistsTheTour() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(repository.updateCount()).isEqualTo(1);
    }

    @Test
    void cancel_reportsHowManyBookingsWereCancelled() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);
        bookingCancellation.willReport(4);

        assertThat(driver.cancel(command(tour)).cancelledBookings()).isEqualTo(4);
    }

    // ── The cross-context call ───────────────────────────────────────────────

    /**
     * The driver passes a <b>tour</b> id, not booking ids. That is the boundary: which
     * bookings are affected is {@code booking}'s decision, so this context never enumerates
     * them and never learns {@code TourBooking}'s state model.
     */
    @Test
    void cancel_addressesTheBookingSideByTourId() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(bookingCancellation.received()).singleElement().satisfies(c -> {
            assertThat(c.tourId()).isEqualTo(TOUR_REF.value());
            assertThat(c.guideTourId()).isEqualTo(tour.id().value().toString());
        });
    }

    @Test
    void cancel_passesReasonToBookingSide() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(bookingCancellation.received()).singleElement()
                .satisfies(c -> assertThat(c.reason()).isEqualTo(REASON));
    }

    /**
     * AC-04. Both contexts must record the same instant, or the tour and its bookings would
     * disagree about when the cancellation happened by however long the call took.
     */
    @Test
    void cancel_passesTheSameInstantToBothSides() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(tour.cancelledAt()).contains(NOW);
        assertThat(bookingCancellation.received()).singleElement()
                .satisfies(c -> assertThat(c.cancelledAt()).isEqualTo(NOW));
    }

    /**
     * The clock is the only source — the command carries no timestamp at all
     * ({@code architecture.definition.md} § 8.1).
     */
    @Test
    void cancel_takesTheCancellationTimeFromTheClock() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(tour.cancelledAt()).contains(NOW);
        assertThat(CancelTourByGuideCommand.class.getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("cancelledAt"));
    }

    // ── AC-05 / AC-06: rejected states ───────────────────────────────────────

    @Test
    void cancel_throwsInvalidGuideTourStateException_whenFinished() {
        final GuideTour tour = preloadedTour(GuideTourStatus.FINISHED);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.cancel(command(tour)));
    }

    /**
     * AC-06. The aggregate is guarded before the cross-context call, so an uncancellable tour
     * costs no work in the other context — and, more importantly, cannot half-cancel its
     * bookings.
     */
    @Test
    void cancel_whenAlreadyCancelled_doesNotCallBookingSide() {
        final GuideTour tour = preloadedTour(GuideTourStatus.CANCELLED);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.cancel(command(tour)));

        assertThat(bookingCancellation.received()).isEmpty();
        assertThat(repository.updateCount()).isZero();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void cancel_whenFinished_doesNotCallBookingSide() {
        final GuideTour tour = preloadedTour(GuideTourStatus.FINISHED);

        try {
            driver.cancel(command(tour));
        } catch (InvalidGuideTourStateException ignored) {
        }

        assertThat(bookingCancellation.received()).isEmpty();
    }

    // ── AC-07: booking side fails ────────────────────────────────────────────

    @Test
    void cancel_propagatesBookingCancellationFailure() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);
        bookingCancellation.willFailWith(new IllegalStateException("booking side down"));

        assertThatExceptionOfType(BookingCancellationFailedException.class)
                .isThrownBy(() -> driver.cancel(command(tour)));
    }

    @Test
    void cancel_whenBookingSideFails_preservesTheCause() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);
        final IllegalStateException cause = new IllegalStateException("booking side down");
        bookingCancellation.willFailWith(cause);

        assertThatExceptionOfType(BookingCancellationFailedException.class)
                .isThrownBy(() -> driver.cancel(command(tour)))
                .withCause(cause);
    }

    /**
     * No event may escape a failed cancellation. `DomainEventPublisher` defers to after
     * commit, so a rollback would discard it anyway — but the driver drains events only after
     * both sides succeed, and this asserts that ordering directly rather than relying on the
     * publisher's behaviour.
     */
    @Test
    void cancel_whenBookingSideFails_publishesNothing() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);
        bookingCancellation.willFailWith(new IllegalStateException("booking side down"));

        try {
            driver.cancel(command(tour));
        } catch (BookingCancellationFailedException ignored) {
        }

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    // ── AC-08: not found ─────────────────────────────────────────────────────

    @Test
    void cancel_throwsGuideTourNotFoundException_whenNotFound() {
        assertThatExceptionOfType(GuideTourNotFoundException.class)
                .isThrownBy(() -> driver.cancel(new CancelTourByGuideCommand(
                        GuideTourId.generate().value().toString(), REASON)));
    }

    @Test
    void cancel_throwsIllegalArgumentException_whenGuideTourIdIsMalformed() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> driver.cancel(
                        new CancelTourByGuideCommand("not-a-uuid", REASON)));
    }

    // ── Event publication ────────────────────────────────────────────────────

    @Test
    void cancel_publishesTourCancelledByGuide() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(command(tour));

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TourCancelledByGuide.class))
                .satisfies(e -> {
                    assertThat(e.tourId()).isEqualTo(TOUR_REF);
                    assertThat(e.cancelledAt()).isEqualTo(NOW);
                    assertThat(e.reason()).isEqualTo(REASON);
                });
    }

    // ── Reason validation ────────────────────────────────────────────────────

    @Test
    void cancel_throwsInvalidCancellationReasonException_whenReasonIsBlank() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> driver.cancel(new CancelTourByGuideCommand(
                        tour.id().value().toString(), "   ")));
    }

    @Test
    void cancel_throwsInvalidCancellationReasonException_whenReasonExceedsTheCeiling() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);
        final String tooLong = "x".repeat(CancellationReason.MAX_LENGTH + 1);

        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> driver.cancel(new CancelTourByGuideCommand(
                        tour.id().value().toString(), tooLong)));
    }

    @Test
    void cancel_validatesReasonBeforeTouchingAnything() {
        assertThatExceptionOfType(InvalidCancellationReasonException.class)
                .isThrownBy(() -> driver.cancel(new CancelTourByGuideCommand(
                        GuideTourId.generate().value().toString(), "  ")));

        assertThat(repository.updateCount()).isZero();
        assertThat(bookingCancellation.received()).isEmpty();
    }

    @Test
    void cancel_withoutReason_isPermitted() {
        final GuideTour tour = preloadedTour(GuideTourStatus.SCHEDULED);

        driver.cancel(new CancelTourByGuideCommand(tour.id().value().toString(), null));

        assertThat(tour.status()).isEqualTo(GuideTourStatus.CANCELLED);
        assertThat(bookingCancellation.received()).singleElement()
                .satisfies(c -> assertThat(c.reason()).isNull());
    }


    // ── Helpers and stubs ────────────────────────────────────────────────────

    private GuideTour preloadedTour(final GuideTourStatus status) {
        final GuideTour tour = GuideTour.reconstitute(
                GuideTourId.generate(), TOUR_REF, SCHEDULED_START, status,
                status == GuideTourStatus.SCHEDULED ? null : SCHEDULED_START,
                status == GuideTourStatus.FINISHED ? NOW : null);
        repository.preload(tour);
        return tour;
    }

    private static CancelTourByGuideCommand command(final GuideTour tour) {
        return new CancelTourByGuideCommand(tour.id().value().toString(), REASON);
    }

    private static class GuideTourStubRepository implements GuideTourRepository {
        private final Map<String, GuideTour> store = new LinkedHashMap<>();
        private int updateCount;

        void preload(final GuideTour tour) {
            store.put(tour.id().value().toString(), tour);
        }

        int updateCount() {
            return updateCount;
        }

        @Override
        public void save(final GuideTour guideTour) {
            store.put(guideTour.id().value().toString(), guideTour);
        }

        @Override
        public Optional<GuideTour> findById(final GuideTourId guideTourId) {
            return Optional.ofNullable(store.get(guideTourId.value().toString()));
        }

        @Override
        public void update(final GuideTour guideTour) {
            updateCount++;
            store.put(guideTour.id().value().toString(), guideTour);
        }
    }

    private static class RecordingBookingCancellation
            implements MarkBookingCancelledByGuideUseCase {

        private final List<MarkBookingCancelledByGuideCommand> commands = new ArrayList<>();
        private int reportedCount = 1;
        private RuntimeException failure;

        void willReport(final int count) {
            this.reportedCount = count;
        }

        void willFailWith(final RuntimeException failure) {
            this.failure = failure;
        }

        List<MarkBookingCancelledByGuideCommand> received() {
            return Collections.unmodifiableList(commands);
        }

        @Override
        public MarkBookingCancelledByGuideResult cancelByGuide(
                final MarkBookingCancelledByGuideCommand command) {
            commands.add(command);
            if (failure != null) {
                throw failure;
            }
            return new MarkBookingCancelledByGuideResult(reportedCount);
        }
    }

    private static class PublishCapturingPublisher implements DomainEventPublisher {
        private final List<DomainEvent> events = new ArrayList<>();

        @Override
        public void publish(final DomainEvent event) {
            events.add(event);
        }

        List<DomainEvent> publishedEvents() {
            return Collections.unmodifiableList(events);
        }
    }

    private record FixedClockPort(Instant fixed) implements ClockPort {
        @Override
        public Instant now() {
            return fixed;
        }
    }
}
