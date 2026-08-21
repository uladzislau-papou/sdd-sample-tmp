package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancelledBy;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCancelledByGuide;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCancelledByGuideCommand;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spring-free unit tests for {@link MarkBookingCancelledByGuideDriver} (UC09).
 *
 * <p>Outport dependencies are in-line stubs ({@code test.definition.md} section 2.2).
 *
 * <p>What these tests deliberately do <em>not</em> cover: that the call runs inside the
 * {@code guide} driver's transaction and that a failure here rolls the tour cancellation
 * back (UC09 section 6). That is Spring transaction wiring rather than driver logic, and
 * asserting it here would test the framework. It belongs to UC12's integration test.
 *
 * <p>SDD: See {@code documentation/use-cases/uc09-cancel-booking-by-guide.spec.md}.
 */
class MarkBookingCancelledByGuideDriverTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final Instant GUIDE_CANCELLED_AT = Instant.parse("2026-03-02T09:15:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);
    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final String GUIDE_TOUR_ID = "guide-tour-7";
    private static final String REASON = "Severe weather warning";

    private final BookingStubRepository repository = new BookingStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final MarkBookingCancelledByGuideDriver driver =
            new MarkBookingCancelledByGuideDriver(repository, publisher, clock);

    private static TourBooking bookingWith(final TourBookingStatus status) {
        return TourBooking.reconstitute(
                BookingId.generate(), TOUR_ID, new TourDate(FUTURE_DATE),
                new ParticipantCount(3), new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"), status);
    }

    private static MarkBookingCancelledByGuideCommand command() {
        return new MarkBookingCancelledByGuideCommand(
                TOUR_ID.value(), GUIDE_CANCELLED_AT, GUIDE_TOUR_ID, REASON);
    }

    // ── AC-01: happy path from CONFIRMED ─────────────────────────────────────

    @Test
    void cancel_fromConfirmed_returnsCancelledStatus() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        assertThat(driver.cancelByGuide(command()).cancelledCount()).isEqualTo(1);
    }

    @Test
    void cancel_fromConfirmed_recordsGuideAttribution() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(booking.cancelledBy()).contains(CancelledBy.GUIDE);
    }

    @Test
    void cancel_fromConfirmed_callsUpdateOnRepository() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(repository.updateCount()).isEqualTo(1);
    }

    // ── AC-02: happy path from ACTIVE ────────────────────────────────────────

    @Test
    void cancel_fromActive_returnsCancelledStatus() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        assertThat(driver.cancelByGuide(command()).cancelledCount()).isEqualTo(1);
    }

    @Test
    void cancel_fromRequested_returnsCancelledStatus() {
        final TourBooking booking = bookingWith(TourBookingStatus.REQUESTED);
        repository.preload(booking);

        assertThat(driver.cancelByGuide(command()).cancelledCount()).isEqualTo(1);
    }

    // ── AC-03: COMPLETED is skipped, not rejected ────────────────────────────

    /**
     * A COMPLETED booking is excluded by `findCancellableByTourId`, so it is never handed to
     * the aggregate and no exception is raised.
     *
     * <p>This is a change from the per-booking design, and a deliberate one: a guide must be
     * able to cancel a tour that has one finished booking on it. Throwing would have rolled
     * back the whole tour cancellation because of a booking nobody was asking to cancel. The
     * aggregate's guard still rejects COMPLETED if anything reaches it directly — see
     * `TourBookingTest.cancel_byGuide_fromCompleted_throwsInvalidBookingStateException` —
     * so the rule is enforced in the domain and merely never triggered from here.
     */
    @Test
    void cancel_skipsCompletedBookings_withoutThrowing() {
        repository.preload(bookingWith(TourBookingStatus.COMPLETED));
        final TourBooking confirmed = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(confirmed);

        final int cancelled = driver.cancelByGuide(command()).cancelledCount();

        assertThat(cancelled).isEqualTo(1);
        assertThat(confirmed.status()).isEqualTo(TourBookingStatus.CANCELLED);
        assertThat(repository.updateCount()).isEqualTo(1);
    }

    // ── AC-04: idempotency ───────────────────────────────────────────────────

    @Test
    void cancel_idempotent_doesNotCallUpdate() {
        final TourBooking booking = bookingWith(TourBookingStatus.CANCELLED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(repository.updateCount()).isZero();
    }

    @Test
    void cancel_idempotent_doesNotPublishEvent() {
        final TourBooking booking = bookingWith(TourBookingStatus.CANCELLED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void cancel_idempotent_returnsZero_whenBookingAlreadyCancelled() {
        repository.preload(bookingWith(TourBookingStatus.CANCELLED));

        assertThat(driver.cancelByGuide(command()).cancelledCount()).isZero();
    }

    // ── AC-05: correlation ───────────────────────────────────────────────────

    @Test
    void cancel_publishesEventCarryingGuideTourIdAndReason() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(BookingCancelledByGuide.class))
                .satisfies(e -> {
                    assertThat(e.guideTourId()).isEqualTo(GUIDE_TOUR_ID);
                    assertThat(e.reason()).isEqualTo(new CancellationReason(REASON));
                });
    }

    // ── Cancellation time: supplied by the caller, not this context ──────────

    /**
     * The guide already recorded when it cancelled the tour, so this driver uses that value
     * rather than its own clock — otherwise the booking and the tour would disagree about
     * one moment by the duration of the call ({@code architecture.definition.md} § 8.1).
     */
    @Test
    void cancel_usesTheCallersCancelledAt_whenSupplied() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(command());

        assertThat(booking.cancelledAt()).contains(GUIDE_CANCELLED_AT);
    }

    @Test
    void cancel_usesClockPort_whenCancelledAtIsNull() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(new MarkBookingCancelledByGuideCommand(
                TOUR_ID.value(), null, GUIDE_TOUR_ID, REASON));

        assertThat(booking.cancelledAt()).contains(NOW);
    }

    // ── A tour with nothing to cancel ────────────────────────────────────────

    /**
     * AC-06 as it now reads. There is no "booking not found" case any more: the command names
     * a tour, and a tour with no cancellable bookings is a successful zero rather than an
     * error. Anything that threw here would roll back the guide's tour cancellation, which
     * would mean a tour could not be called off simply because nobody had booked it.
     */
    @Test
    void cancel_returnsZero_whenTourHasNoBookings() {
        assertThat(driver.cancelByGuide(command()).cancelledCount()).isZero();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void cancel_returnsZero_whenEveryBookingIsAlreadyTerminal() {
        repository.preload(bookingWith(TourBookingStatus.CANCELLED));
        repository.preload(bookingWith(TourBookingStatus.COMPLETED));

        assertThat(driver.cancelByGuide(command()).cancelledCount()).isZero();
        assertThat(repository.updateCount()).isZero();
    }

    // ── Reason validation ────────────────────────────────────────────────────

    @Test
    void cancel_throwsInvalidBookingRequestException_whenReasonIsBlank() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> driver.cancelByGuide(new MarkBookingCancelledByGuideCommand(
                        TOUR_ID.value(), GUIDE_CANCELLED_AT, GUIDE_TOUR_ID, "  ")));
    }

    @Test
    void cancel_withoutReason_stillCancels() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        driver.cancelByGuide(new MarkBookingCancelledByGuideCommand(
                TOUR_ID.value(), GUIDE_CANCELLED_AT, GUIDE_TOUR_ID, null));

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
        assertThat(booking.cancellationReason()).isEmpty();
    }

    /**
     * `TourId` rejects null and blank in its own constructor, so a caller that supplies
     * neither a tour nor a fallback fails fast rather than cancelling nothing and reporting
     * success — which is what a silent zero would look like.
     */
    @Test
    void cancel_throwsIllegalArgumentException_whenTourIdIsBlank() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> driver.cancelByGuide(new MarkBookingCancelledByGuideCommand(
                        "  ", GUIDE_CANCELLED_AT, GUIDE_TOUR_ID, REASON)));
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class BookingStubRepository implements TourBookingRepository {
        private final Map<String, TourBooking> store = new LinkedHashMap<>();
        private int updateCount;

        void preload(final TourBooking booking) {
            store.put(booking.bookingId().value().toString(), booking);
        }

        int updateCount() {
            return updateCount;
        }

        @Override
        public void save(final TourBooking booking) {
            store.put(booking.bookingId().value().toString(), booking);
        }

        @Override
        public Optional<TourBooking> findById(final BookingId bookingId) {
            return Optional.ofNullable(store.get(bookingId.value().toString()));
        }

        @Override
        public void update(final TourBooking booking) {
            updateCount++;
            store.put(booking.bookingId().value().toString(), booking);
        }

        @Override
        public List<TourBooking> findConfirmedByTourId(final TourId tourId) {
            throw new UnsupportedOperationException("not used in UC09 driver tests");
        }

        @Override
        public List<TourBooking> findActiveByTourId(final TourId tourId) {
            throw new UnsupportedOperationException("not used in UC09 driver tests");
        }

        @Override
        public List<TourBooking> findCancellableByTourId(final TourId tourId) {
            return store.values().stream()
                    .filter(b -> b.tourId().equals(tourId))
                    .filter(b -> b.status() != TourBookingStatus.CANCELLED
                            && b.status() != TourBookingStatus.COMPLETED)
                    .toList();
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
