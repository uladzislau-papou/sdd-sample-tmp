package com.dominikgaller.alpinebooking.booking.core.domain;

import com.dominikgaller.alpinebooking.booking.core.domain.event.TourStarted;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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

        assertThat(tour.startedAt()).isEqualTo(AFTER_SCHEDULED_START);
    }

    @Test
    void start_emitsTourStartedEvent() {
        final GuideTour tour = scheduledTour();

        tour.start(AFTER_SCHEDULED_START);

        final List<DomainEvent> events = tour.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TourStarted.class);
        final TourStarted event = (TourStarted) events.get(0);
        assertThat(event.guideTourId()).isEqualTo(TOUR_ID);
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
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.RUNNING, AFTER_SCHEDULED_START);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.start(AFTER_SCHEDULED_START));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenFinished() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.FINISHED, AFTER_SCHEDULED_START);

        assertThatExceptionOfType(InvalidGuideTourStateException.class)
                .isThrownBy(() -> tour.start(AFTER_SCHEDULED_START));
    }

    @Test
    void start_throwsInvalidGuideTourStateException_whenCancelled() {
        final GuideTour tour = GuideTour.reconstitute(
                TOUR_ID, TOUR_REF, SCHEDULED_START, GuideTourStatus.CANCELLED, null);

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
