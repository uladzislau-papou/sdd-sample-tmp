package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.event.ParticipantsChanged;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ChangeParticipantsCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ChangeParticipantsResult;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityChecker;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityUnavailableException;
import com.dominikgaller.alpinebooking.booking.core.outport.ClockPort;
import com.dominikgaller.alpinebooking.booking.core.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
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
 * Spring-free unit tests for {@link ChangeParticipantsDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class ChangeParticipantsDriverTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);
    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final TourDate TOUR_DATE = new TourDate(FUTURE_DATE);
    private static final AvailableCapacity CAPACITY_10 = new AvailableCapacity(10);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final FixedAvailabilityChecker availabilityChecker = new FixedAvailabilityChecker(CAPACITY_10);
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final ChangeParticipantsDriver driver =
            new ChangeParticipantsDriver(repository, availabilityChecker, publisher, clock);

    private TourBooking requestedBooking() {
        final TourBooking booking = TourBooking.request(
                BookingId.generate(),
                TOUR_ID,
                TOUR_DATE,
                new ParticipantCount(3),
                CAPACITY_10,
                new ParticipantContact("Alice", "alice@example.com"),
                NOW);
        booking.pullDomainEvents(); // drain TourBookingRequested — simulates reconstitution from DB
        return booking;
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void change_returnsUpdatedParticipantCount() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        final ChangeParticipantsResult result = driver.change(
                new ChangeParticipantsCommand(booking.bookingId().value().toString(), 5));

        assertThat(result.participantCount()).isEqualTo(5);
    }

    @Test
    void change_callsUpdateOnRepository() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.change(new ChangeParticipantsCommand(booking.bookingId().value().toString(), 5));

        assertThat(repository.updatedBookings()).hasSize(1);
        assertThat(repository.updatedBookings().get(0).participantCount().value()).isEqualTo(5);
    }

    @Test
    void change_publishesParticipantsChangedEvent() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.change(new ChangeParticipantsCommand(booking.bookingId().value().toString(), 5));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(ParticipantsChanged.class);
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    void change_throwsBookingNotFoundException_whenNotFound() {
        final String unknownId = BookingId.generate().value().toString();

        assertThatExceptionOfType(BookingNotFoundException.class)
                .isThrownBy(() -> driver.change(new ChangeParticipantsCommand(unknownId, 5)));
    }

    // ── Invalid state ────────────────────────────────────────────────────────

    @Test
    void change_throwsInvalidBookingStateException_whenCancelled() {
        final TourBooking booking = TourBooking.reconstitute(
                BookingId.generate(), TOUR_ID, TOUR_DATE,
                new ParticipantCount(3), CAPACITY_10,
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.CANCELLED);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.change(
                        new ChangeParticipantsCommand(booking.bookingId().value().toString(), 5)));
    }

    // ── Capacity exceeded ─────────────────────────────────────────────────────

    @Test
    void change_throwsCapacityExceededException_whenNewCountExceedsCapacity() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);
        availabilityChecker.setCapacity(new AvailableCapacity(2));

        assertThatExceptionOfType(CapacityExceededException.class)
                .isThrownBy(() -> driver.change(
                        new ChangeParticipantsCommand(booking.bookingId().value().toString(), 10)));
    }

    // ── Availability unavailable ──────────────────────────────────────────────

    @Test
    void change_propagatesAvailabilityUnavailableException() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);
        availabilityChecker.failWithUnavailable();

        assertThatExceptionOfType(AvailabilityUnavailableException.class)
                .isThrownBy(() -> driver.change(
                        new ChangeParticipantsCommand(booking.bookingId().value().toString(), 5)));
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

        List<TourBooking> updatedBookings() {
            return Collections.unmodifiableList(updated);
        }
    }

    private static class FixedAvailabilityChecker implements AvailabilityChecker {
        private AvailableCapacity capacity;
        private boolean throwUnavailable = false;

        FixedAvailabilityChecker(final AvailableCapacity capacity) {
            this.capacity = capacity;
        }

        void setCapacity(final AvailableCapacity capacity) {
            this.capacity = capacity;
        }

        void failWithUnavailable() {
            this.throwUnavailable = true;
        }

        @Override
        public AvailableCapacity checkAvailability(final TourId tourId, final TourDate tourDate) {
            if (throwUnavailable) {
                throw new AvailabilityUnavailableException("stub unavailable");
            }
            return capacity;
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
