package com.dominikgaller.alpinebooking.guide.outbound.persistence.write;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.jooq.tables.records.GuideTourRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure mapping utility: converts between the {@link GuideTour} aggregate and
 * the jOOQ {@link GuideTourRecord}.
 *
 * <p>No Spring dependency. No IO. {@code TIMESTAMP} columns are stored as UTC
 * {@link LocalDateTime} and converted to/from {@link Instant} using UTC offset.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
class GuideTourMapper {

    GuideTourRecord toRecord(final GuideTour tour) {
        final GuideTourRecord record = new GuideTourRecord();
        record.setId(tour.id().value().toString());
        record.setTourId(tour.tourId().value());
        record.setScheduledStart(LocalDateTime.ofInstant(tour.scheduledStart(), ZoneOffset.UTC));
        record.setStatus(tour.status().name());
        record.setStartedAt(
                Optional.ofNullable(tour.startedAt())
                        .map(i -> LocalDateTime.ofInstant(i, ZoneOffset.UTC))
                        .orElse(null));
        return record;
    }

    GuideTour toDomain(final GuideTourRecord record) {
        final Instant startedAt = Optional.ofNullable(record.getStartedAt())
                .map(ldt -> ldt.toInstant(ZoneOffset.UTC))
                .orElse(null);
        return GuideTour.reconstitute(
                new GuideTourId(UUID.fromString(record.getId())),
                new TourId(record.getTourId()),
                record.getScheduledStart().toInstant(ZoneOffset.UTC),
                GuideTourStatus.valueOf(record.getStatus()),
                startedAt
        );
    }
}
