package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CompleteTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CompleteTourResult;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCompleted;
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
 * Spring-free unit tests for {@link CompleteTourDriver}.
 *
 * <p>Outport dependencies are in-line stubs, stubs over mocks
 * ({@code test.definition.md} section 2.2).
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md} section 7.
 */
class CompleteTourDriverTest {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-06-15T09:05:00Z");
    private static final Instant CLOCK_NOW = Instant.parse("2026-06-15T17:00:00Z");
    private static final Instant EXPLICIT_COMPLETION = Instant.parse("2026-06-15T18:30:00Z");
    private static final TourId TOUR_ID = new TourId("TOUR-42");

    private final GuideTourStubRepository repository = new GuideTourStubRepository();
    private final PublishCapturingPublisher publisher = new PublishCapturingPublisher();
    private final FixedClockPort clock = new FixedClockPort(CLOCK_NOW);

    private final CompleteTourDriver driver =
            new CompleteTourDriver(repository, publisher, clock);

    private GuideTour runningTour() {
        return GuideTour.reconstitute(
                GuideTourId.generate(), TOUR_ID, SCHEDULED_START,
                GuideTourStatus.RUNNING, STARTED_AT, null);
    }

    private GuideTour tourInStatus(final GuideTourStatus status) {
        return GuideTour.reconstitute(
                GuideTourId.generate(), TOUR_ID, SCHEDULED_START, status, STARTED_AT, null);
    }

    private static CompleteTourCommand command(final GuideTour tour) {
        return new CompleteTourCommand(tour.id().value().toString(), Optional.empty());
    }

    private static CompleteTourCommand command(final GuideTour tour, final Instant at) {
        return new CompleteTourCommand(tour.id().value().toString(), Optional.of(at));
    }

    // ── AC-01 / AC-02: happy path ────────────────────────────────────────────

    @Test
    void complete_happyPath_returnsFinishedStatus() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        final CompleteTourResult result = driver.complete(command(tour));

        assertThat(result.status()).isEqualTo("FINISHED");
    }

    @Test
    void complete_happyPath_callsUpdateOnRepository() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        driver.complete(command(tour));

        assertThat(repository.updatedTours()).containsExactly(tour);
    }

    @Test
    void complete_happyPath_publishesTourCompletedEvent() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        driver.complete(command(tour));

        assertThat(publisher.publishedEvents()).singleElement()
                .isInstanceOf(TourCompleted.class);
    }

    @Test
    void complete_usesClockPort_whenCompletedAtIsEmpty() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        driver.complete(command(tour));

        assertThat(tour.completedAt()).contains(CLOCK_NOW);
    }

    @Test
    void complete_usesProvidedCompletedAt_whenPresent() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        driver.complete(command(tour, EXPLICIT_COMPLETION));

        assertThat(tour.completedAt()).contains(EXPLICIT_COMPLETION);
    }

    // ── AC-05: not found ─────────────────────────────────────────────────────

    @Test
    void complete_throwsGuideTourNotFoundException_whenNotFound() {
        final String unknownId = GuideTourId.generate().value().toString();

        assertThatExceptionOfType(GuideTourNotFoundException.class)
                .isThrownBy(() -> driver.complete(
                        new CompleteTourCommand(unknownId, Optional.empty())));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    @Test
    void complete_throwsIllegalArgumentException_whenIdIsMalformed() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> driver.complete(
                        new CompleteTourCommand("not-a-uuid", Optional.empty())));
    }

    // ── AC-04: invalid state ─────────────────────────────────────────────────

    @Test
    void complete_throwsInvalidGuideTourStateException_whenScheduled() {
        final GuideTour tour = tourInStatus(GuideTourStatus.SCHEDULED);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.complete(command(tour)));
    }

    @Test
    void complete_throwsInvalidGuideTourStateException_whenAlreadyFinished() {
        final GuideTour tour = tourInStatus(GuideTourStatus.FINISHED);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.complete(command(tour)));
    }

    @Test
    void complete_doesNotCallUpdate_whenStateInvalid() {
        final GuideTour tour = tourInStatus(GuideTourStatus.CANCELLED);
        repository.preload(tour);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> driver.complete(command(tour)));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
    }

    // ── AC-03: completed before start ────────────────────────────────────────

    @Test
    void complete_propagatesTourCompletedBeforeStartException() {
        final GuideTour tour = runningTour();
        repository.preload(tour);

        assertThatExceptionOfType(TourCompletedBeforeStartException.class)
                .isThrownBy(() -> driver.complete(
                        command(tour, STARTED_AT.minusSeconds(1))));

        assertThat(repository.updatedTours()).isEmpty();
        assertThat(publisher.publishedEvents()).isEmpty();
        assertThat(tour.status()).isEqualTo(GuideTourStatus.RUNNING);
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
