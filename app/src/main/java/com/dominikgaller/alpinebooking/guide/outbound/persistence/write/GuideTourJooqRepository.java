package com.dominikgaller.alpinebooking.guide.outbound.persistence.write;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.jooq.tables.records.GuideTourRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.dominikgaller.alpinebooking.jooq.Tables.GUIDE_TOUR;

/**
 * jOOQ-backed implementation of {@link GuideTourRepository}.
 *
 * <p>Supports insert ({@link #save}), point-lookup ({@link #findById}),
 * and state update ({@link #update}) on the {@code guide_tour} table.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
@Repository
public class GuideTourJooqRepository implements GuideTourRepository {

    private final DSLContext dsl;
    private final GuideTourMapper mapper = new GuideTourMapper();

    public GuideTourJooqRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(final GuideTour guideTour) {
        final GuideTourRecord record = mapper.toRecord(guideTour);
        dsl.insertInto(GUIDE_TOUR).set(record).execute();
    }

    @Override
    public Optional<GuideTour> findById(final GuideTourId guideTourId) {
        final GuideTourRecord record = dsl
                .selectFrom(GUIDE_TOUR)
                .where(GUIDE_TOUR.ID.eq(guideTourId.value().toString()))
                .fetchOne();
        return Optional.ofNullable(record).map(mapper::toDomain);
    }

    @Override
    public void update(final GuideTour guideTour) {
        final LocalDateTime startedAt = guideTour.startedAt()
                .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                .orElse(null);
        final int rowsUpdated = dsl
                .update(GUIDE_TOUR)
                .set(GUIDE_TOUR.STATUS, guideTour.status().name())
                .set(GUIDE_TOUR.STARTED_AT, startedAt)
                .where(GUIDE_TOUR.ID.eq(guideTour.id().value().toString()))
                .execute();
        if (rowsUpdated != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly 1 row for guide tour " +
                    guideTour.id().value() + " but updated " + rowsUpdated);
        }
    }
}
