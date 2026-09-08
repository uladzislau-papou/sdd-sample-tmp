package com.example.contractmanagement.booking.core.domain.tourbooking

/**
 * Lifecycle of a [TourBooking].
 *
 * `CANCELLED` and `COMPLETED` are terminal. The transitions that reach each state are
 * defined by the aggregate, not here — an enum lists states, it does not own the rules.
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
enum class TourBookingStatus {
    REQUESTED,
    CONFIRMED,
    CANCELLED,
    ACTIVE,
    COMPLETED,
}
