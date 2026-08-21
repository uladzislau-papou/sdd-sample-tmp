package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCompleted;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCompletedCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCompletedResult;
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
 * Spring-free unit tests for {@link MarkBookingCompletedDriver}.
 *
 * <p>Mirrors {@code MarkBookingActiveDriverTest}. Outport dependencies are in-line stubs.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md} section 7.
 */
class MarkBookingCompletedDriverTest {

    private static final Instant NOW = Instant.parse("2026-06-15T17:00:00Z");
    private static final Instant EXPLICIT_COMPLETION = Instant.parse("2026-06-15T18:30:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);
    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final String GUIDE_TOUR_ID = "guide-tour-7";

    private final BookingStubRepository repository = new BookingStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final MarkBookingCompletedDriver driver =
            new MarkBookingCompletedDriver(repository, publisher, clock);

    private TourBooking bookingWith(final TourBookingStatus status) {
        return TourBooking.reconstitute(
                BookingId.generate(), TOUR_ID, new TourDate(FUTURE_DATE),
                new ParticipantCount(3), new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"), status);
    }

    private static MarkBookingCompletedCommand command(final TourBooking booking) {
        return new MarkBookingCompletedCommand(booking.bookingId().value().toString(), null, GUIDE_TOUR_ID);
    }

    // ── AC-01: happy path ────────────────────────────────────────────────────

    @Test
    void markCompleted_happyPath_returnsCompletedStatus() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        final MarkBookingCompletedResult result = driver.markCompleted(command(booking));

        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    void markCompleted_happyPath_callsUpdateOnRepository() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(repository.updatedBookings()).containsExactly(booking);
    }

    @Test
    void markCompleted_happyPath_publishesBookingCompletedEvent() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(publisher.publishedEvents()).singleElement()
                .isInstanceOf(BookingCompleted.class);
    }

    // ── AC-03: clock resolution ──────────────────────────────────────────────

    @Test
    void markCompleted_usesClockPort_whenCompletedAtIsNull() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(BookingCompleted.class))
                .satisfies(e -> assertThat(e.completedAt()).isEqualTo(NOW));
    }

    @Test
    void markCompleted_usesProvidedCompletedAt_whenNotNull() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        driver.markCompleted(new MarkBookingCompletedCommand(
                booking.bookingId().value().toString(), EXPLICIT_COMPLETION, GUIDE_TOUR_ID));

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(BookingCompleted.class))
                .satisfies(e -> assertThat(e.completedAt()).isEqualTo(EXPLICIT_COMPLETION));
    }

    /**
     * The driver's own hop in the correlation chain: `guideTourId` arrives on the command and
     * must reach the published event. `TourBookingTest` covers the aggregate's hop and
     * `TourCompletedListenerTest` the listener's; without this one the middle link is
     * unasserted and a driver that dropped the field would still pass everywhere else.
     */
    @Test
    void markCompleted_carriesGuideTourIdFromCommandOntoPublishedEvent() {
        final TourBooking booking = bookingWith(TourBookingStatus.ACTIVE);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(BookingCompleted.class))
                .satisfies(e -> assertThat(e.guideTourId()).isEqualTo(GUIDE_TOUR_ID));
    }

    // ── AC-02: idempotency ───────────────────────────────────────────────────

    @Test
    void markCompleted_idempotent_whenAlreadyCompleted_doesNotPublishEvent() {
        final TourBooking booking = bookingWith(TourBookingStatus.COMPLETED);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void markCompleted_idempotent_whenAlreadyCompleted_doesNotCallUpdate() {
        final TourBooking booking = bookingWith(TourBookingStatus.COMPLETED);
        repository.preload(booking);

        driver.markCompleted(command(booking));

        assertThat(repository.updatedBookings()).isEmpty();
    }

    @Test
    void markCompleted_idempotent_whenAlreadyCompleted_returnsCompletedStatus() {
        final TourBooking booking = bookingWith(TourBookingStatus.COMPLETED);
        repository.preload(booking);

        final MarkBookingCompletedResult result = driver.markCompleted(command(booking));

        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    // ── AC-06: not found ─────────────────────────────────────────────────────

    @Test
    void markCompleted_throwsBookingNotFoundException_whenNotFound() {
        final String unknownId = BookingId.generate().value().toString();

        assertThatExceptionOfType(BookingNotFoundException.class)
                .isThrownBy(() -> driver.markCompleted(
                        new MarkBookingCompletedCommand(unknownId, null, GUIDE_TOUR_ID)));
    }

    // ── AC-04 / AC-05: invalid state ─────────────────────────────────────────

    @Test
    void markCompleted_throwsInvalidBookingStateException_whenConfirmed() {
        final TourBooking booking = bookingWith(TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.markCompleted(command(booking)));
    }

    @Test
    void markCompleted_throwsInvalidBookingStateException_whenCancelled() {
        final TourBooking booking = bookingWith(TourBookingStatus.CANCELLED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.markCompleted(command(booking)));
    }

    @Test
    void markCompleted_doesNotCallUpdate_whenStateInvalid() {
        final TourBooking booking = bookingWith(TourBookingStatus.REQUESTED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.markCompleted(command(booking)));

        assertThat(repository.updatedBookings()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class BookingStubRepository implements TourBookingRepository {

        @Override
        public List<TourBooking> findCancellableByTourId(final TourId tourId) {
            throw new UnsupportedOperationException("not used in this test");
        }
        private final Map<String, TourBooking> store = new LinkedHashMap<>();
        private final List<TourBooking> updated = new ArrayList<>();

        void preload(final TourBooking booking) {
            store.put(booking.bookingId().value().toString(), booking);
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
            updated.add(booking);
        }

        @Override
        public List<TourBooking> findConfirmedByTourId(final TourId tourId) {
            throw new UnsupportedOperationException("not used in UC07 driver tests");
        }

        @Override
        public List<TourBooking> findActiveByTourId(final TourId tourId) {
            return store.values().stream()
                    .filter(b -> b.tourId().equals(tourId))
                    .filter(b -> b.status() == TourBookingStatus.ACTIVE)
                    .toList();
        }

        List<TourBooking> updatedBookings() {
            return Collections.unmodifiableList(updated);
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

    private record FixedClockPort(Instant instant) implements ClockPort {
        @Override
        public Instant now() {
            return instant;
        }
    }
}
