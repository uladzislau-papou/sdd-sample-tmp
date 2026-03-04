package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingConfirmed;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ConfirmTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ConfirmTourBookingResult;
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
 * Spring-free unit tests for {@link ConfirmTourBookingDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class ConfirmTourBookingDriverTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private final ConfirmTourBookingDriver driver =
            new ConfirmTourBookingDriver(repository, publisher, clock);

    private TourBooking requestedBooking() {
        final BookingId id = BookingId.generate();
        final TourBooking booking = TourBooking.request(
                id,
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                NOW);
        booking.pullDomainEvents(); // drain TourBookingRequested — simulates reconstitution from DB
        return booking;
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void confirm_happyPath_returnsConfirmedStatus() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        final ConfirmTourBookingResult result =
                driver.confirm(new ConfirmTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(result.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirm_happyPath_callsUpdateOnRepository() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.confirm(new ConfirmTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(repository.updatedBookings()).hasSize(1);
        assertThat(repository.updatedBookings().get(0).status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirm_happyPath_publishesTourBookingConfirmedEvent() {
        final TourBooking booking = requestedBooking();
        repository.preload(booking);

        driver.confirm(new ConfirmTourBookingCommand(booking.bookingId().value().toString()));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(TourBookingConfirmed.class);
    }

    // ── Not found ────────────────────────────────────────────────────────────

    @Test
    void confirm_throwsBookingNotFoundException_whenNotFound() {
        final String unknownId = BookingId.generate().value().toString();

        assertThatExceptionOfType(BookingNotFoundException.class)
                .isThrownBy(() -> driver.confirm(new ConfirmTourBookingCommand(unknownId)));
    }

    // ── Invalid state ────────────────────────────────────────────────────────

    @Test
    void confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed() {
        final TourBooking booking = requestedBooking();
        booking.confirm(NOW); // transition to CONFIRMED
        booking.pullDomainEvents(); // drain events so the stub starts clean
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.confirm(
                        new ConfirmTourBookingCommand(booking.bookingId().value().toString())));
    }

    @Test
    void confirm_doesNotCallUpdate_whenStateInvalid() {
        final TourBooking booking = requestedBooking();
        booking.confirm(NOW);
        booking.pullDomainEvents();
        repository.preload(booking);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> driver.confirm(
                        new ConfirmTourBookingCommand(booking.bookingId().value().toString())));

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
