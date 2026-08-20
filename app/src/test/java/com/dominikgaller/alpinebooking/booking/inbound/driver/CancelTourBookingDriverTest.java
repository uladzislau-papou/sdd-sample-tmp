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
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.TourBookingCancelled;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.CancelTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.CancelTourBookingResult;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
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
 * Spring-free unit tests for {@link CancelTourBookingDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class CancelTourBookingDriverTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final CancelTourBookingDriver driver =
            new CancelTourBookingDriver(repository, publisher, clock);

    private TourBooking requestedBooking() {
        final TourBooking booking = TourBooking.request(
                BookingId.generate(),
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                NOW);
        booking.pullDomainEvents(); // drain TourBookingRequested — simulates reconstitution from DB
        return booking;
    }

    private TourBooking confirmedBooking() {
        final TourBooking booking = requestedBooking();
        booking.confirm(NOW);
        booking.pullDomainEvents(); // drain TourBookingConfirmed
        return booking;
    }

    // ── Happy path: cancel from REQUESTED ────────────────────────────────────

    @Test
    void cancel_fromRequested_returnsCancelledStatus() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        final CancelTourBookingResult result =
                driver.cancel(new CancelTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_fromRequested_callsUpdateOnRepository() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.cancel(new CancelTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(repository.updatedBookings()).hasSize(1);
        assertThat(repository.updatedBookings().get(0).status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    @Test
    void cancel_fromRequested_publishesTourBookingCancelledEvent() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.cancel(new CancelTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(TourBookingCancelled.class);
    }

    // ── Happy path: cancel from CONFIRMED ────────────────────────────────────

    @Test
    void cancel_fromConfirmed_returnsCancelledStatus() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        final CancelTourBookingResult result =
                driver.cancel(new CancelTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_fromConfirmed_publishesTourBookingCancelledEvent() {
        final TourBooking booking = confirmedBooking();
        repository.preload(booking);

        driver.cancel(new CancelTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(TourBookingCancelled.class);
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    void cancel_throwsBookingNotFoundException_whenNotFound() {
        final String unknownId = BookingId.generate().value().toString();

        assertThatExceptionOfType(BookingNotFoundException.class)
                .isThrownBy(() -> driver.cancel(new CancelTourBookingCommand(unknownId)));
    }

    // ── Invalid state ────────────────────────────────────────────────────────

    @Test
    void cancel_throwsInvalidBookingStateException_whenActive() {
        final TourBooking booking = TourBooking.reconstitute(
                BookingId.generate(),
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.ACTIVE);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.cancel(
                        new CancelTourBookingCommand(booking.bookingId().value().toString())));
    }

    @Test
    void cancel_doesNotCallUpdate_whenStateInvalid() {
        final TourBooking booking = TourBooking.reconstitute(
                BookingId.generate(),
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                TourBookingStatus.ACTIVE);
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.cancel(
                        new CancelTourBookingCommand(booking.bookingId().value().toString())));

        assertThat(repository.updatedBookings()).isEmpty();
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
        public java.util.List<TourBooking> findByTourId(final com.dominikgaller.alpinebooking.shared.domain.TourId tourId) {
            throw new UnsupportedOperationException("not used in UC03 tests");
        }

        @Override
        public java.util.List<TourBooking> findConfirmedByTourId(final com.dominikgaller.alpinebooking.shared.domain.TourId tourId) {
            throw new UnsupportedOperationException("not used in UC03 tests");
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
