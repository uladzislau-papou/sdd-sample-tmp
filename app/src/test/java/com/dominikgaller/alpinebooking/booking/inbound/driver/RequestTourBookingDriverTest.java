package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.RequestTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.RequestTourBookingResult;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Spring-free unit tests for {@link RequestTourBookingDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class RequestTourBookingDriverTest {

    // Fixed clock: any tour date after 2026-03-03 counts as "future"
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final SaveCapturingRepository repository = new SaveCapturingRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(NOW);

    private RequestTourBookingDriver driverWith(final AvailabilityChecker checker) {
        return new RequestTourBookingDriver(repository, checker, publisher, clock);
    }

    private RequestTourBookingCommand validCommand() {
        return new RequestTourBookingCommand(
                "TOUR-42", FUTURE_DATE, 3, "Alice", "alice@example.com");
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void happyPath_returnsNonNullBookingIdAndStatusRequested() {
        final RequestTourBookingResult result =
                driverWith(new FixedAvailabilityChecker(10)).request(validCommand());

        assertThat(result.bookingId()).isNotNull();
        assertThat(result.status()).isEqualTo("REQUESTED");
    }

    @Test
    void happyPath_repositoryReceivesExactlyOneSaveCall() {
        driverWith(new FixedAvailabilityChecker(10)).request(validCommand());

        assertThat(repository.savedBookings()).hasSize(1);
    }

    @Test
    void happyPath_publisherReceivesExactlyOneTourBookingRequested() {
        driverWith(new FixedAvailabilityChecker(10)).request(validCommand());

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(TourBookingRequested.class);
    }

    // ── Capacity exceeded ────────────────────────────────────────────────────

    @Test
    void capacityExceeded_throwsCapacityExceededExceptionAndSaveNotCalled() {
        assertThatExceptionOfType(CapacityExceededException.class)
                .isThrownBy(() -> driverWith(new FixedAvailabilityChecker(0)).request(validCommand()));

        assertThat(repository.savedBookings()).isEmpty();
    }

    // ── Availability infrastructure failure ──────────────────────────────────

    @Test
    void availabilityFailure_propagatesExceptionAndSaveNotCalled() {
        assertThatExceptionOfType(AvailabilityUnavailableException.class)
                .isThrownBy(() -> driverWith(new ThrowingAvailabilityChecker()).request(validCommand()));

        assertThat(repository.savedBookings()).isEmpty();
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class SaveCapturingRepository implements TourBookingRepository {
        private final List<TourBooking> saved = new ArrayList<>();

        @Override
        public void save(final TourBooking booking) {
            saved.add(booking);
        }

        @Override
        public Optional<TourBooking> findById(final BookingId bookingId) {
            throw new UnsupportedOperationException("not used in UC01 tests");
        }

        @Override
        public void update(final TourBooking booking) {
            throw new UnsupportedOperationException("not used in UC01 tests");
        }

        List<TourBooking> savedBookings() {
            return Collections.unmodifiableList(saved);
        }
    }

    private static class FixedAvailabilityChecker implements AvailabilityChecker {
        private final AvailableCapacity capacity;

        FixedAvailabilityChecker(final int capacity) {
            this.capacity = new AvailableCapacity(capacity);
        }

        @Override
        public AvailableCapacity checkAvailability(final TourId tourId, final TourDate tourDate) {
            return capacity;
        }
    }

    private static class ThrowingAvailabilityChecker implements AvailabilityChecker {
        @Override
        public AvailableCapacity checkAvailability(final TourId tourId, final TourDate tourDate) {
            throw new AvailabilityUnavailableException("Stub: infrastructure failure");
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
