package com.dominikgaller.alpinebooking.guide.outbound.persistence.write;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.CancellationReason;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.dominikgaller.alpinebooking.jooq.Tables.GUIDE_TOUR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence integration tests for {@link GuideTourJooqRepository}.
 *
 * <p>Starts a minimal Spring context ({@link PersistenceTestApplication}) with H2 in-memory
 * database (profile {@code test}). Flyway migrations run automatically before each test. Each
 * test method is rolled back via {@link Transactional}.
 */
@SpringBootTest(classes = GuidePersistenceTestApplication.class)
@ActiveProfiles("test")
@Transactional
class GuideTourJooqRepositoryIT {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-06-15T09:02:00Z");
    private static final TourId TOUR_REF = new TourId("TOUR-42");
    private static final Instant NOW = Instant.parse("2026-06-15T11:30:00Z");

    @Autowired
    private GuideTourJooqRepository repository;

    @Autowired
    private DSLContext dsl;

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_persistsAllFields() {
        final GuideTour tour = sampleTour();

        repository.save(tour);

        final var record = dsl.selectFrom(GUIDE_TOUR)
                .where(GUIDE_TOUR.ID.eq(tour.id().value().toString()))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getId()).isEqualTo(tour.id().value().toString());
        assertThat(record.getTourId()).isEqualTo(TOUR_REF.value());
        assertThat(record.getStatus()).isEqualTo("SCHEDULED");
        assertThat(record.getStartedAt()).isNull();
    }

    @Test
    void save_persistsScheduledStart_asUtcLocalDateTime() {
        final GuideTour tour = sampleTour();

        repository.save(tour);

        final var record = dsl.selectFrom(GUIDE_TOUR)
                .where(GUIDE_TOUR.ID.eq(tour.id().value().toString()))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getScheduledStart().toInstant(java.time.ZoneOffset.UTC))
                .isEqualTo(SCHEDULED_START);
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertThat(repository.findById(GuideTourId.generate())).isEmpty();
    }

    @Test
    void findById_returnsAggregate_afterSave() {
        final GuideTour tour = sampleTour();
        repository.save(tour);

        final var found = repository.findById(tour.id());

        assertThat(found).isPresent();
        final GuideTour loaded = found.get();
        assertThat(loaded.id()).isEqualTo(tour.id());
        assertThat(loaded.tourId()).isEqualTo(TOUR_REF);
        assertThat(loaded.scheduledStart()).isEqualTo(SCHEDULED_START);
        assertThat(loaded.status()).isEqualTo(GuideTourStatus.SCHEDULED);
        assertThat(loaded.startedAt()).isEmpty();
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_changesStatus_andStartedAt_afterStart() {
        final GuideTour tour = sampleTour();
        repository.save(tour);

        tour.start(STARTED_AT);
        repository.update(tour);

        final var reloaded = repository.findById(tour.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(GuideTourStatus.RUNNING);
        assertThat(reloaded.get().startedAt()).contains(STARTED_AT);
    }

    /**
     * UC11 — {@code completedAt} is mutable aggregate state, so it must survive an update.
     * The equivalent gap on {@code TourBooking} silently discarded a whole use case's
     * effect; see {@code ports/tour-booking-repository.outport.spec.md} section 2.3.
     */
    @Test
    void update_changesStatus_andCompletedAt_afterComplete() {
        final GuideTour tour = sampleTour();
        repository.save(tour);
        tour.start(STARTED_AT);
        repository.update(tour);

        final Instant completedAt = STARTED_AT.plusSeconds(3600);
        tour.complete(completedAt);
        repository.update(tour);

        final var reloaded = repository.findById(tour.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(GuideTourStatus.FINISHED);
        assertThat(reloaded.get().completedAt()).contains(completedAt);
        assertThat(reloaded.get().startedAt()).contains(STARTED_AT);
    }

    @Test
    void completedAt_isEmpty_forATourThatWasNeverCompleted() {
        final GuideTour tour = sampleTour();
        repository.save(tour);

        final var loaded = repository.findById(tour.id()).orElseThrow();

        assertThat(loaded.completedAt()).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GuideTour sampleTour() {
        return GuideTour.schedule(GuideTourId.generate(), TOUR_REF, SCHEDULED_START);
    }

    /** A persisted SCHEDULED tour — the precondition for UC12's cancellation tests. */
    private GuideTour savedScheduledTour() {
        final GuideTour tour = sampleTour();
        repository.save(tour);
        return tour;
    }

    /**
     * UC12 — the CANCELLED transition round-trip, including both new columns. The port spec's
     * standing obligation demands this for every mutable field added; `cancelled_at` and
     * `cancellation_reason` are the third and fourth.
     */
    @Test
    void update_changesStatus_toCancelled() {
        final GuideTour tour = savedScheduledTour();

        tour.cancel(NOW, new CancellationReason("Severe weather warning"));
        repository.update(tour);

        final var reloaded = repository.findById(tour.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(GuideTourStatus.CANCELLED);
        assertThat(reloaded.get().cancelledAt()).contains(NOW);
        assertThat(reloaded.get().cancellationReason()).contains(new CancellationReason("Severe weather warning"));
    }

    /**
     * UC12 — cancelling without a reason leaves the column null rather than empty-string, and
     * a live tour comes back with both fields empty. Guards the mapper in both directions: a
     * coerced default would make every tour look cancelled.
     */
    @Test
    void cancellationFields_areEmpty_forATourThatWasNeverCancelled() {
        final GuideTour tour = savedScheduledTour();

        final var reloaded = repository.findById(tour.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().cancelledAt()).isEmpty();
        assertThat(reloaded.get().cancellationReason()).isEmpty();
    }

    @Test
    void update_persistsCancellation_withoutReason() {
        final GuideTour tour = savedScheduledTour();

        tour.cancel(NOW, null);
        repository.update(tour);

        final var reloaded = repository.findById(tour.id());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().cancellationReason()).isEmpty();
        assertThat(reloaded.get().cancelledAt()).contains(NOW);
    }

    /**
     * UC12 — the schema's column width and the domain's ceiling must not drift apart.
     *
     * <p>`guide.CancellationReason.MAX_LENGTH` is 400 and `guide_tour.cancellation_reason` is
     * `VARCHAR(400)`, and nothing but this test connects them. The booking side has the
     * analogue; this one was missing, and `V5__DDL_add_guide_tour_cancellation.sql` claimed it
     * existed. `ddd-hex-reviewer` caught the false claim — the comment is now true.
     *
     * <p>Equality, not "at least": a wider column would tolerate values the domain rejects,
     * and someone would eventually read the column as the real limit.
     */
    @Test
    void cancellationReasonColumnWidth_matchesTheDomainCeiling() {
        assertThat(GUIDE_TOUR.CANCELLATION_REASON.getDataType().length())
                .isEqualTo(CancellationReason.MAX_LENGTH);
    }
}
