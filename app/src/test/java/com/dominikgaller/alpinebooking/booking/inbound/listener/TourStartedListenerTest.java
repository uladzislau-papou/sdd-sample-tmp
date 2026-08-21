package com.dominikgaller.alpinebooking.booking.inbound.listener;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingActiveCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingActiveResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingActiveUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.TourStarted;
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

/**
 * Spring-free unit tests for {@link TourStartedListener}.
 *
 * <p>Covers the UC06 fan-out: a single {@code TourStarted} event must activate every
 * CONFIRMED booking for that tour and leave every other booking alone. Outport and
 * inport dependencies are in-line stubs ({@code test.definition.md} section 2.2).
 *
 * <p>What these tests deliberately do <em>not</em> cover: the
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code REQUIRES_NEW} semantics
 * from ADR 0002. Those are Spring wiring, not listener logic, and asserting them here
 * would test the framework ({@code test.definition.md} section 8). They need a Spring
 * integration test, which does not exist yet.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md} section 5.
 */
class TourStartedListenerTest {

    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final TourId OTHER_TOUR_ID = new TourId("TOUR-99");
    private static final Instant STARTED_AT = Instant.parse("2026-06-15T09:00:00Z");
    private static final String GUIDE_TOUR_ID = "guide-tour-7";
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final RecordingMarkBookingActive useCase = new RecordingMarkBookingActive();

    private final TourStartedListener listener =
            new TourStartedListener(repository, useCase);

    private static TourBooking bookingWith(final TourId tourId, final TourBookingStatus status) {
        return TourBooking.reconstitute(
                BookingId.generate(),
                tourId,
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                status);
    }

    private static TourStarted event() {
        return new TourStarted(GUIDE_TOUR_ID, TOUR_ID, STARTED_AT);
    }

    @Test
    void onTourStarted_activatesEveryConfirmedBookingForTheTour() {
        final TourBooking first = bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED);
        final TourBooking second = bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED);
        repository.preload(first);
        repository.preload(second);

        listener.onTourStarted(event());

        assertThat(useCase.activatedBookingIds())
                .containsExactlyInAnyOrder(
                        first.bookingId().value().toString(),
                        second.bookingId().value().toString());
    }

    @Test
    void onTourStarted_propagatesStartedAtAndGuideTourIdFromEvent() {
        final TourBooking booking = bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED);
        repository.preload(booking);

        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).singleElement().satisfies(command -> {
            assertThat(command.startedAt()).isEqualTo(STARTED_AT);
            assertThat(command.guideTourId()).isEqualTo(GUIDE_TOUR_ID);
        });
    }

    @Test
    void onTourStarted_doesNotActivateRequestedBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.REQUESTED));

        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourStarted_doesNotActivateCancelledBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CANCELLED));

        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourStarted_doesNotActivateAlreadyActiveBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.ACTIVE));

        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourStarted_doesNotActivateCompletedBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.COMPLETED));

        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourStarted_activatesOnlyConfirmed_whenStatusesAreMixed() {
        final TourBooking confirmed = bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED);
        repository.preload(confirmed);
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CANCELLED));
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.ACTIVE));

        listener.onTourStarted(event());

        assertThat(useCase.activatedBookingIds())
                .containsExactly(confirmed.bookingId().value().toString());
    }

    @Test
    void onTourStarted_ignoresBookingsForOtherTours() {
        final TourBooking sameTour = bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED);
        repository.preload(sameTour);
        repository.preload(bookingWith(OTHER_TOUR_ID, TourBookingStatus.CONFIRMED));

        listener.onTourStarted(event());

        assertThat(useCase.activatedBookingIds())
                .containsExactly(sameTour.bookingId().value().toString());
    }

    @Test
    void onTourStarted_noBookingsForTour_doesNothing() {
        listener.onTourStarted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class BookingStubRepository implements TourBookingRepository {
        private final Map<String, TourBooking> store = new LinkedHashMap<>();

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
            store.put(booking.bookingId().value().toString(), booking);
        }

        @Override
        public List<TourBooking> findConfirmedByTourId(final TourId tourId) {
            return store.values().stream()
                    .filter(b -> b.tourId().equals(tourId))
                    .filter(b -> b.status() == TourBookingStatus.CONFIRMED)
                    .toList();
        }

        @Override
        public List<TourBooking> findActiveByTourId(final TourId tourId) {
            return store.values().stream()
                    .filter(b -> b.tourId().equals(tourId))
                    .filter(b -> b.status() == TourBookingStatus.ACTIVE)
                    .toList();
        }

        @Override
        public List<TourBooking> findCancellableByTourId(final TourId tourId) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static class RecordingMarkBookingActive implements MarkBookingActiveUseCase {
        private final List<MarkBookingActiveCommand> commands = new ArrayList<>();

        @Override
        public MarkBookingActiveResult markActive(final MarkBookingActiveCommand command) {
            commands.add(command);
            return new MarkBookingActiveResult("ACTIVE");
        }

        List<MarkBookingActiveCommand> receivedCommands() {
            return Collections.unmodifiableList(commands);
        }

        List<String> activatedBookingIds() {
            return commands.stream().map(MarkBookingActiveCommand::bookingId).toList();
        }
    }
}
