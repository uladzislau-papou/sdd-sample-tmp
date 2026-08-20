package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.domain.event.TourStarted;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
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
 * <p>All outport dependencies are replaced by in-line stub implementations
 * (stubs over mocks, {@code test.definition.md} section 2.2). No Spring context is loaded.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md} section 7 and
 *          {@code documentation/ports/start-tour.inport.spec.md}.
 */
class StartTourDriverTest {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant CLOCK_NOW = Instant.parse("2026-06-15T09:30:00Z");
    private static final Instant EXPLICIT_START = Instant.parse("2026-06-15T10:15:00Z");
    private static final TourId TOUR_ID = new TourId("TOUR-42");

    private final GuideTourStubRepository repository = new GuideTourStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(CLOCK_NOW);

    private final StartTourDriver driver =
            new StartTourDriver(repository, publisher, clock);

    private GuideTour scheduledTour() {
        return GuideTour.schedule(GuideTourId.generate(), TOUR_ID, SCHEDULED_START);
    }

    private GuideTour tourInStatus(final GuideTourStatus status) {
        return GuideTour.reconstitute(
                GuideTourId.generate(), TOUR_ID, SCHEDULED_START, status, null);
    }

    private static StartTourCommand command(final GuideTour tour) {
        return new StartTourCommand(tour.id().value().toString(), Optional.empty());
    }

    private static StartTourCommand command(final GuideTour tour, final Instant startedAt) {
        return new StartTourCommand(tour.id().value().toString(), Optional.of(startedAt));
    }

    // ── AC-01 / AC-02: happy path ────────────────────────────────────────────

    @Test
    void start_happyPath_returnsRunningStatus() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        final StartTourResult result = driver.start(command(tour));

        assertThat(result.status()).isEqualTo("RUNNING");
    }

    @Test
    void start_happyPath_callsUpdateOnRepository() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(command(tour));

        assertThat(repository.updatedTours()).containsExactly(tour);
    }

    @Test
    void start_happyPath_publishesTourStartedEvent() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(command(tour));

        assertThat(publisher.publishedEvents())
                .singleElement()
                .isInstanceOf(TourStarted.class);
    }

    @Test
    void start_publishedEventCarriesGuideTourIdAndTourId() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(command(tour));

        assertThat(publisher.publishedEvents()).singleElement()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TourStarted.class))
                .satisfies(event -> {
                    assertThat(event.guideTourId()).isEqualTo(tour.id().value().toString());
                    assertThat(event.tourId()).isEqualTo(TOUR_ID);
                    assertThat(event.startedAt()).isEqualTo(CLOCK_NOW);
                });
    }

    // ── AC-01 vs AC-02: clock resolution ────────────────────────────────────

    @Test
    void start_usesClockPort_whenStartedAtIsEmpty() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(command(tour));

        assertThat(tour.startedAt()).isEqualTo(CLOCK_NOW);
    }

    @Test
    void start_usesProvidedStartedAt_whenPresent() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        driver.start(command(tour, EXPLICIT_START));

        assertThat(tour.startedAt()).isEqualTo(EXPLICIT_START);
    }

    // ── AC-05: not found ─────────────────────────────────────────────────────

    @Test
    void start_throwsGuideTourNotFoundException_whenNotFound() {
        final String unknownId = GuideTourId.generate().value().toString();

        assertThatExceptionOfType(GuideTourNotFoundException.class)
                .isThrownBy(() -> driver.start(
                        new StartTourCommand(unknownId, Optional.empty())));
    }

    @Test
    void start_doesNotCallUpdate_whenNotFound() {
        final String unknownId = GuideTourId.generate().value().toString();

        assertThatExceptionOfType(GuideTourNotFoundException.class)
                .isThrownBy(() -> driver.start(
                        new StartTourCommand(unknownId, Optional.empty())));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    // ── AC-04: invalid state ─────────────────────────────────────────────────

    @Test
    void start_throwsInvalidGuideTourStateException_whenAlreadyRunning() {
        final GuideTour tour = tourInStatus(GuideTourStatus.RUNNING);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.start(command(tour)));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenFinished() {
        final GuideTour tour = tourInStatus(GuideTourStatus.FINISHED);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.start(command(tour)));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenCancelled() {
        final GuideTour tour = tourInStatus(GuideTourStatus.CANCELLED);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.start(command(tour)));
    }

    @Test
    void start_doesNotCallUpdate_whenStateInvalid() {
        final GuideTour tour = tourInStatus(GuideTourStatus.RUNNING);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.start(command(tour)));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    // ── AC-03: too early ─────────────────────────────────────────────────────

    @Test
    void start_propagatesTourStartTooEarlyException_whenBeforeScheduledStart() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        assertThatExceptionOfType(TourStartTooEarlyException.class)
                .isThrownBy(() -> driver.start(
                        command(tour, SCHEDULED_START.minusSeconds(1))));
    }

    @Test
    void start_doesNotCallUpdate_whenTooEarly() {
        final GuideTour tour = scheduledTour();
        repository.preload(tour);

        assertThatExceptionOfType(TourStartTooEarlyException.class)
                .isThrownBy(() -> driver.start(
                        command(tour, SCHEDULED_START.minusSeconds(1))));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
        assertThat(tour.status()).isEqualTo(GuideTourStatus.SCHEDULED);
    }

    // ── Stub implementations ─────────────────────────────────────────────────

    private static class GuideTourStubRepository implements GuideTourRepository {
        private final Map<String, GuideTour> store = new LinkedHashMap<>();
        private final List<GuideTour> updated = new ArrayList<>();

        void preload(final GuideTour tour) {
            store.put(tour.id().value().toString(), tour);
        }

        @Override
        public void save(final GuideTour tour) {
            store.put(tour.id().value().toString(), tour);
        }

        @Override
        public Optional<GuideTour> findById(final GuideTourId guideTourId) {
            return Optional.ofNullable(store.get(guideTourId.value().toString()));
        }

        @Override
        public void update(final GuideTour tour) {
            updated.add(tour);
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
