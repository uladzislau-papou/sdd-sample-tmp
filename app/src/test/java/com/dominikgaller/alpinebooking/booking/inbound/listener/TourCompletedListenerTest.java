package com.dominikgaller.alpinebooking.booking.inbound.listener;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCompletedCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCompletedResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCompletedUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCompleted;
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
 * Spring-free unit tests for {@link TourCompletedListener}.
 *
 * <p>Covers the UC07 fan-out: a single {@code TourCompleted} event must complete every
 * ACTIVE booking for that tour and leave every other booking alone. Outport and
 * inport dependencies are in-line stubs ({@code test.definition.md} section 2.2).
 *
 * <p>What these tests deliberately do <em>not</em> cover: the
 * {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code REQUIRES_NEW} semantics
 * from ADR 0002. Those are Spring wiring, not listener logic, and asserting them here
 * would test the framework ({@code test.definition.md} section 8). They need a Spring
 * integration test, which does not exist yet.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md} section 5.
 */
class TourCompletedListenerTest {

    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final TourId OTHER_TOUR_ID = new TourId("TOUR-99");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-15T09:00:00Z");
    private static final String GUIDE_TOUR_ID = "guide-tour-7";
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private final BookingStubRepository repository = new BookingStubRepository();
    private final RecordingMarkBookingCompleted useCase = new RecordingMarkBookingCompleted();

    private final TourCompletedListener listener =
            new TourCompletedListener(repository, useCase);

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

    private static TourCompleted event() {
        return new TourCompleted(GUIDE_TOUR_ID, TOUR_ID, COMPLETED_AT);
    }

    @Test
    void onTourCompleted_completesEveryActiveBookingForTheTour() {
        final TourBooking first = bookingWith(TOUR_ID, TourBookingStatus.ACTIVE);
        final TourBooking second = bookingWith(TOUR_ID, TourBookingStatus.ACTIVE);
        repository.preload(first);
        repository.preload(second);

        listener.onTourCompleted(event());

        assertThat(useCase.completedBookingIds())
                .containsExactlyInAnyOrder(
                        first.bookingId().value().toString(),
                        second.bookingId().value().toString());
    }

    @Test
    void onTourCompleted_propagatesCompletedAtAndGuideTourIdFromEvent() {
        final TourBooking booking = bookingWith(TOUR_ID, TourBookingStatus.ACTIVE);
        repository.preload(booking);

        listener.onTourCompleted(event());

        assertThat(useCase.receivedCommands()).singleElement().satisfies(command -> {
            assertThat(command.completedAt()).isEqualTo(COMPLETED_AT);
            assertThat(command.guideTourId()).isEqualTo(GUIDE_TOUR_ID);
        });
    }

    @Test
    void onTourCompleted_doesNotCompleteRequestedBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.REQUESTED));

        listener.onTourCompleted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourCompleted_doesNotCompleteCancelledBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CANCELLED));

        listener.onTourCompleted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourCompleted_doesNotCompleteConfirmedBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED));

        listener.onTourCompleted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    /**
     * Already-COMPLETED bookings are filtered out by the query, so the idempotent no-op on
     * the aggregate is never even reached. Both layers hold: this asserts the query's half.
     */
    @Test
    void onTourCompleted_doesNotCompleteAlreadyCompletedBooking() {
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.COMPLETED));

        listener.onTourCompleted(event());

        assertThat(useCase.receivedCommands()).isEmpty();
    }

    @Test
    void onTourCompleted_completesOnlyActive_whenStatusesAreMixed() {
        final TourBooking active = bookingWith(TOUR_ID, TourBookingStatus.ACTIVE);
        repository.preload(active);
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CANCELLED));
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.COMPLETED));
        repository.preload(bookingWith(TOUR_ID, TourBookingStatus.CONFIRMED));

        listener.onTourCompleted(event());

        assertThat(useCase.completedBookingIds())
                .containsExactly(active.bookingId().value().toString());
    }

    @Test
    void onTourCompleted_ignoresBookingsForOtherTours() {
        final TourBooking sameTour = bookingWith(TOUR_ID, TourBookingStatus.ACTIVE);
        repository.preload(sameTour);
        repository.preload(bookingWith(OTHER_TOUR_ID, TourBookingStatus.ACTIVE));

        listener.onTourCompleted(event());

        assertThat(useCase.completedBookingIds())
                .containsExactly(sameTour.bookingId().value().toString());
    }

    @Test
    void onTourCompleted_noBookingsForTour_doesNothing() {
        listener.onTourCompleted(event());

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
            throw new UnsupportedOperationException("not used in UC07 listener tests");
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

    private static class RecordingMarkBookingCompleted implements MarkBookingCompletedUseCase {
        private final List<MarkBookingCompletedCommand> commands = new ArrayList<>();

        @Override
        public MarkBookingCompletedResult markCompleted(final MarkBookingCompletedCommand command) {
            commands.add(command);
            return new MarkBookingCompletedResult("COMPLETED");
        }

        List<MarkBookingCompletedCommand> receivedCommands() {
            return Collections.unmodifiableList(commands);
        }

        List<String> completedBookingIds() {
            return commands.stream().map(MarkBookingCompletedCommand::bookingId).toList();
        }
    }
}
