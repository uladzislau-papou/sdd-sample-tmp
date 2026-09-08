package com.example.contractmanagement.booking.core.domain.tourbooking

import java.util.UUID

/**
 * Identity of a [TourBooking].
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
data class BookingId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): BookingId = BookingId(value)

        fun generate(): BookingId = BookingId(UUID.randomUUID())
    }
}
