package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.GuideTour;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourId;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourStarted;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.booking.core.outport.ClockPort;
import com.dominikgaller.alpinebooking.booking.core.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
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
 * Spring-free unit tests for {@link StartTourDriver}.
 *
 * <p>All outport dependencies are replaced by in-line stub implementations.
 * No Spring context is loaded.
 */
class StartTourDriverTest {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant AFTER_SCHEDULED_START = Instant.parse("2026-06-15T09:05:00Z");
    private static final Instant BEFORE_SCHEDULED_START = Instant.parse("2026-06-15T08:55:00Z");
    private static final TourId TOUR_REF = new TourId("TOUR-42");

    private final GuideTourStubRepository repository = new GuideTourStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(AFTER_SCHEDULED_START);

    private final StartTourDriver driver = new StartTourDriver(repository, publisher, clock);

    private GuideTour scheduledTour() {
        return GuideTour.schedule(GuideTourId.generate(), TOUR_REF, SCHEDULED_START);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void start_returnsRunningStatus() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        final StartTourResult result = driver.start(
                new StartTourCommand(tour.id().value().toString(), Optional.of(AFTER_SCHEDULED_START)));

        assertThat(result.status()).isEqualTo("RUNNING");
    }

    @Test
    void start_callsUpdateOnRepository() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(new StartTourCommand(tour.id().value().toString(), Optional.of(AFTER_SCHEDULED_START)));

        assertThat(repository.updatedTours()).hasSize(1);
        assertThat(repository.updatedTours().get(0).status()).isEqualTo(GuideTourStatus.RUNNING);
    }

    @Test
    void start_publishesTourStartedEvent() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(new StartTourCommand(tour.id().value().toString(), Optional.of(AFTER_SCHEDULED_START)));

        assertThat(publisher.publishedEvents()).hasSize(1);
        assertThat(publisher.publishedEvents().get(0)).isInstanceOf(TourStarted.class);
    }

    @Test
    void start_usesClockNow_whenStartedAtAbsent() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(new StartTourCommand(tour.id().value().toString(), Optional.empty()));

        // clock returns AFTER_SCHEDULED_START — tour should have transitioned to RUNNING
        assertThat(repository.updatedTours()).hasSize(1);
        assertThat(repository.updatedTours().get(0).startedAt()).isEqualTo(AFTER_SCHEDULED_START);
    }

    // ── Not found ─────────────────────────────────────────────────────────────

    @Test
    void start_throwsGuideTourNotFoundException_whenNotFound() {
        final String unknownId = GuideTourId.generate().value().toString();

        assertThatExceptionOfType(GuideTourNotFoundException.class)
                .isThrownBy(() -> driver.start(
                        new StartTourCommand(unknownId, Optional.of(AFTER_SCHEDULED_START))));
    }

    // ── Invalid state ─────────────────────────────────────────────────────────

    @Test
    void start_throwsInvalidGuideTourStateException_whenAlreadyRunning() {
        final GuideTour tour = GuideTour.reconstitute(
                GuideTourId.generate(), TOUR_REF, SCHEDULED_START,
                GuideTourStatus.RUNNING, AFTER_SCHEDULED_START);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.start(
                        new StartTourCommand(tour.id().value().toString(),
                                Optional.of(AFTER_SCHEDULED_START))));
    }

    // ── Too early ─────────────────────────────────────────────────────────────

    @Test
    void start_throwsTourStartTooEarlyException_whenBeforeScheduledStart() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        assertThatExceptionOfType(TourStartTooEarlyException.class)
                .isThrownBy(() -> driver.start(
                        new StartTourCommand(tour.id().value().toString(),
                                Optional.of(BEFORE_SCHEDULED_START))));
    }

    // ── Stub implementations ──────────────────────────────────────────────────

    private static class GuideTourStubRepository implements GuideTourRepository {
        private final Map<String, GuideTour> store = new LinkedHashMap<>();
        private final List<GuideTour> updated = new ArrayList<>();

        void preload(final GuideTour tour) {
            store.put(tour.id().value().toString(), tour);
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
            updated.add(guideTour);
        }

        List<GuideTour> updatedTours() {
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
