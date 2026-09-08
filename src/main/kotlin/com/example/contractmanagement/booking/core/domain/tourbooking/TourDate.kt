package com.example.contractmanagement.booking.core.domain.tourbooking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The scheduled calendar date of a tour.
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
data class TourDate(
    val value: LocalDate,
) {
    /**
     * Whether this date is strictly after the calendar day of [now], in UTC.
     *
     * Takes the current instant as a parameter rather than reading a clock: the domain
     * must stay deterministic, so time enters through `ClockPort` at the driver and
     * arrives here as data (`architecture.definition.md` § 8).
     */
    fun isInFuture(now: Instant): Boolean = value.isAfter(now.atZone(ZoneOffset.UTC).toLocalDate())
}
