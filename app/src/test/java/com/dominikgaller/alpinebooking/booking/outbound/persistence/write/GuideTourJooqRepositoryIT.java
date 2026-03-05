package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.GuideTour;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourId;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
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
@SpringBootTest(classes = PersistenceTestApplication.class)
@ActiveProfiles("test")
@Transactional
class GuideTourJooqRepositoryIT {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-06-15T09:02:00Z");
    private static final TourId TOUR_REF = new TourId("TOUR-42");

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
        assertThat(loaded.startedAt()).isNull();
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
        assertThat(reloaded.get().startedAt()).isEqualTo(STARTED_AT);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GuideTour sampleTour() {
        return GuideTour.schedule(GuideTourId.generate(), TOUR_REF, SCHEDULED_START);
    }
}
