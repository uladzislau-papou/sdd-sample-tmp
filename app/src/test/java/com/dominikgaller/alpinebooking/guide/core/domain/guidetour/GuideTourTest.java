package com.dominikgaller.alpinebooking.guide.core.domain.guidetour;

import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.TourStarted;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCompleted;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class GuideTourTest {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant AT_SCHEDULED_START = SCHEDULED_START;
    private static final Instant AFTER_SCHEDULED_START = Instant.parse("2026-06-15T09:05:00Z");
    private static final Instant BEFORE_SCHEDULED_START = Instant.parse("2026-06-15T08:55:00Z");

    private static final GuideTourId TOUR_ID = GuideTourId.generate();
    private static final TourId TOUR_REF = new TourId("TOUR-42");

    private GuideTour scheduledTour() {
        return GuideTour.schedule(TOUR_ID, TOUR_REF, SCHEDULED_START);
    }

    // ── Construction guards (G-01, G-02) ─────────────────────────────────────

    @Test
    void schedule_throwsNullPointerException_whenIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> GuideTour.schedule(null, TOUR_REF, SCHEDULED_START));
    }

    @Test
    void schedule_throwsNullPointerException_whenTourIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> GuideTour.schedule(TOUR_ID, null, SCHEDULED_START));
    }

    @Test
    void schedule_throwsNullPointerException_whenScheduledStartIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> GuideTour.schedule(TOUR_ID, TOUR_REF, null));
    }

    @Test
    void start_throwsNullPointerException_whenStartedAtIsNull() {
        final GuideTour tour = scheduledTour();

        assertThatNullPointerException().isThrownBy(() -> tour.start(null));
    }

    /**
     * G-04: {@code startedAt()} previously returned {@code null} before the tour started,
     * against {@code coding-style.definition.md} section 1.4.
     */
    @Test
    void startedAt_isEmpty_beforeTheTourStarts() {
        assertThat(scheduledTour().startedAt()).isEmpty();
    }

    @Test
    void startedAt_isPresent_afterTheTourStarts() {
        final GuideTour tour = scheduledTour();

        tour.start(AT_SCHEDULED_START);

        assertThat(tour.startedAt()).contains(AT_SCHEDULED_START);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void start_transitionsToRunning_whenStartedAtEqualsScheduledStart() {
        final GuideTour tour = scheduledTour();

        tour.start(AT_SCHEDULED_START);

        assertThat(tour.status()).isEqualTo(GuideTourStatus.RUNNING);
    }

    @Test
    void start_transitionsToRunning_whenStartedAtIsAfterScheduledStart() {
        final GuideTour tour = scheduledTour();

        tour.start(AFTER_SCHEDULED_START);

        assertThat(tour.status()).isEqualTo(GuideTourStatus.RUNNING);
    }

    @Test
    void start_setsStartedAt() {
        final GuideTour tour = scheduledTour();

        tour.start(AFTER_SCHEDULED_START);

        assertThat(tour.startedAt()).contains(AFTER_SCHEDULED_START);
    }

    @Test
    void start_emitsTourStartedEvent() {
        final GuideTour tour = scheduledTour();

        tour.start(AFTER_SCHEDULED_START);

        final List<DomainEvent> events = tour.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TourStarted.class);
        final TourStarted event = (TourStarted) events.get(0);
        assertThat(event.guideTourId()).isEqualTo(TOUR_ID.value().toString());
        assertThat(event.tourId()).isEqualTo(TOUR_REF);
        assertThat(event.startedAt()).isEqualTo(AFTER_SCHEDULED_START);
    }

    @Test
    void pullDomainEvents_returnsEmptyOnSecondCall() {
        final GuideTour tour = scheduledTour();
        tour.start(AFTER_SCHEDULED_START);
        tour.pullDomainEvents();

        assertThat(tour.pullDomainEvents()).isEmpty();
    }

    // ── UC11: complete ────────────────────────────────────────────────────────

    private GuideTour runningTour() {
        final GuideTour tour = scheduledTour();
        tour.start(AT_SCHEDULED_START);
        tour.pullDomainEvents();
        return tour;
    }

    @Test
    void complete_transitionsToFinished() {
        final GuideTour tour = runningTour();

        tour.complete(AFTER_SCHEDULED_START);

        assertThat(tour.status()).isEqualTo(GuideTourStatus.FINISHED);
    }

    @Test
    void complete_setsCompletedAt() {
        final GuideTour tour = runningTour();

        tour.complete(AFTER_SCHEDULED_START);

        assertThat(tour.completedAt()).contains(AFTER_SCHEDULED_START);
    }

    @Test
    void completedAt_isEmpty_beforeCompletion() {
        assertThat(runningTour().completedAt()).isEmpty();
    }

    @Test
    void complete_emitsTourCompletedEvent() {
        final GuideTour tour = runningTour();

        tour.complete(AFTER_SCHEDULED_START);

        final List<DomainEvent> events = tour.pullDomainEvents();
        assertThat(events).singleElement().isInstanceOf(TourCompleted.class);
        final TourCompleted event = (TourCompleted) events.get(0);
        assertThat(event.guideTourId()).isEqualTo(TOUR_ID.value().toString());
        assertThat(event.tourId()).isEqualTo(TOUR_REF);
        assertThat(event.completedAt()).isEqualTo(AFTER_SCHEDULED_START);
    }

    @Test
    void complete_throwsNullPointerException_whenCompletedAtIsNull() {
        final GuideTour tour = runningTour();

        assertThatNullPointerException().isThrownBy(() -> tour.complete(null));
    }

    @Test
    void complete_throwsTourCompletedBeforeStartException_whenBeforeStartedAt() {
        final GuideTour tour = runningTour();

        assertThatExceptionOfType(TourCompletedBeforeStartException.class)
                .isThrownBy(() -> tour.complete(BEFORE_SCHEDULED_START));
    }

    @Test
    void complete_beforeStart_doesNotChangeStatus() {
        final GuideTour tour = runningTour();

        try {
            tour.complete(BEFORE_SCHEDULED_START);
        } catch (TourCompletedBeforeStartException ignored) {
        }

        assertThat(tour.status()).isEqualTo(GuideTourStatus.RUNNING);
        assertThat(tour.completedAt()).isEmpty();
    }

    @Test
    void complete_throwsInvalidGuideTourStateException_whenScheduled() {
        final GuideTour tour = scheduledTour();

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.complete(AFTER_SCHEDULED_START));
    }

    @Test
    void complete_throwsInvalidGuideTourStateException_whenAlreadyFinished() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.FINISHED,
                AT_SCHEDULED_START, AFTER_SCHEDULED_START);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.complete(AFTER_SCHEDULED_START));
    }

    /**
     * A RUNNING tour with no {@code startedAt} is a state only corrupt data or misuse of
     * {@code reconstitute} can produce — the column is nullable and has no CHECK. Before
     * this guard, {@code complete} dereferenced {@code startedAt} and raised a bare
     * {@code NullPointerException}, i.e. a 500. It is a data-integrity fault rather than a
     * business-rule violation, so {@code IllegalStateException} with a message naming the
     * aggregate is the honest signal ({@code coding-style.definition.md} § 6.2).
     */
    @Test
    void complete_throwsIllegalStateException_whenRunningWithoutStartedAt() {
        final GuideTour corrupt = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.RUNNING, null, null);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> corrupt.complete(AFTER_SCHEDULED_START))
                .withMessageContaining("startedAt");
    }

    @Test
    void complete_throwsInvalidGuideTourStateException_whenCancelled() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.CANCELLED, null, null);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.complete(AFTER_SCHEDULED_START));
    }

    // ── TooEarly ──────────────────────────────────────────────────────────────

    @Test
    void start_throwsTourStartTooEarlyException_whenBeforeScheduledStart() {
        final GuideTour tour = scheduledTour();

        assertThatExceptionOfType(TourStartTooEarlyException.class)
                .isThrownBy(() -> tour.start(BEFORE_SCHEDULED_START));
    }

    @Test
    void start_tooEarly_doesNotChangeStatus() {
        final GuideTour tour = scheduledTour();

        try {
            tour.start(BEFORE_SCHEDULED_START);
        } catch (TourStartTooEarlyException ignored) {
        }

        assertThat(tour.status()).isEqualTo(GuideTourStatus.SCHEDULED);
    }

    // ── Invalid state ─────────────────────────────────────────────────────────

    @Test
    void start_throwsInvalidGuideTourStateException_whenAlreadyRunning() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.RUNNING, AFTER_SCHEDULED_START, null);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.start(AFTER_SCHEDULED_START));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenFinished() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.FINISHED, AFTER_SCHEDULED_START, null);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.start(AFTER_SCHEDULED_START));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenCancelled() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.CANCELLED, null, null);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.start(AFTER_SCHEDULED_START));
    }

    // ── schedule factory ──────────────────────────────────────────────────────

    @Test
    void schedule_setsInitialStatusToScheduled() {
        final GuideTour tour = scheduledTour();

        assertThat(tour.status()).isEqualTo(GuideTourStatus.SCHEDULED);
    }

    @Test
    void schedule_hasNoPendingEvents() {
        final GuideTour tour = scheduledTour();

        assertThat(tour.pullDomainEvents()).isEmpty();
    }
}
