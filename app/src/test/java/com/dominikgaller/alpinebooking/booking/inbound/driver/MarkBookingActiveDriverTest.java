package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingActivated;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingActiveCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingActiveResult;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
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
 * Spring-free unit tests for {@link MarkBookingActiveDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class MarkBookingActiveDriverTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final Instant CUSTOM_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final MarkBookingActiveDriver driver =
            new MarkBookingActiveDriver(repository, publisher, clock);

    private TourBooking confirmedBooking() {
        final BookingId id = BookingId.generate();
        return TourBooking.reconstitute(
                id,
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.CONFIRMED);
    }

    private TourBooking activeBooking() {
        final BookingId id = BookingId.generate();
        return TourBooking.reconstitute(
                id,
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.ACTIVE);
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void markActive_happyPath_returnsActiveStatus() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        final MarkBookingActiveResult result = driver.markActive(
                new MarkBookingActiveCommand(booking.bookingId().value().toString(), null, null));

        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void markActive_happyPath_callsUpdateOnRepository() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), null, null));

        assertThat(repository.updatedBookings()).hasSize(1);
        assertThat(repository.updatedBookings().get(0).status()).isEqualTo(TourBookingStatus.ACTIVE);
    }

    @Test
    void markActive_happyPath_publishesBookingActivatedEvent() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), null, "GT-001"));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(BookingActivated.class);
        final BookingActivated event = (BookingActivated) publisher.publishedEvents().get(0);
        assertThat(event.guideTourId()).isEqualTo("GT-001");
    }

    // ── Clock resolution ─────────────────────────────────────────────────────

    @Test
    void markActive_usesClockPort_whenStartedAtIsNull() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), null, null));

        final BookingActivated event = (BookingActivated) publisher.publishedEvents().get(0);
        assertThat(event.activatedAt()).isEqualTo(NOW);
    }

    @Test
    void markActive_usesProvidedStartedAt_whenNotNull() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), CUSTOM_START, null));

        final BookingActivated event = (BookingActivated) publisher.publishedEvents().get(0);
        assertThat(event.activatedAt()).isEqualTo(CUSTOM_START);
    }

    // ── Idempotency ──────────────────────────────────────────────────────────

    @Test
    void markActive_idempotent_whenAlreadyActive_doesNotPublishEvent() {
        final TourBooking booking = activeBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), null, null));

        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void markActive_idempotent_whenAlreadyActive_doesNotCallUpdate() {
        final TourBooking booking = activeBooking();
        repository.preload(booking);

        driver.markActive(new MarkBookingActiveCommand(
                booking.bookingId().value().toString(), null, null));

        assertThat(repository.updatedBookings()).isEmpty();
    }

    @Test
    void markActive_idempotent_whenAlreadyActive_returnsActiveStatus() {
        final TourBooking booking = activeBooking();
        repository.preload(booking);

        final MarkBookingActiveResult result = driver.markActive(
                new MarkBookingActiveCommand(booking.bookingId().value().toString(), null, null));

        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    void markActive_throwsBookingNotFoundException_whenNotFound() {
        final String unknownId = BookingId.generate().value().toString();

        assertThatExceptionOfType(BookingNotFoundException.class)
                .isThrownBy(() -> driver.markActive(
                        new MarkBookingActiveCommand(unknownId, null, null)));
    }

    // ── Invalid state ────────────────────────────────────────────────────────

    @Test
    void markActive_throwsInvalidBookingStateException_whenCancelled() {
        final TourBooking booking = TourBooking.reconstitute(
                BookingId.generate(),
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.CANCELLED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.markActive(
                        new MarkBookingActiveCommand(
                                booking.bookingId().value().toString(), null, null)));
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class BookingStubRepository implements TourBookingRepository {
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
        public List<TourBooking> findByTourId(final TourId tourId) {
            return store.values().stream()
                    .filter(b -> b.tourId().equals(tourId))
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
