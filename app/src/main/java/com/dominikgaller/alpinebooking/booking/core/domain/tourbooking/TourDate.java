package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Value object representing the scheduled date of a tour.
 *
 * <p>Wraps a non-null {@link LocalDate}. Temporal comparisons use UTC.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record TourDate(LocalDate value) {

    public TourDate {
        Objects.requireNonNull(value, "TourDate value must not be null");
    }

    /**
     * Returns {@code true} if the tour date is strictly after the given instant (UTC).
     *
     * @param now the reference point in time; must not be null
     * @return true if this date is in the future relative to {@code now}
     */
    public boolean isInFuture(final Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        final LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        return value.isAfter(today);
    }
}
