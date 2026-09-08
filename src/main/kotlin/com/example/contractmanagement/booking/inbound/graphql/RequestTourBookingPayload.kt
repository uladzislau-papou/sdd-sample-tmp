package com.example.contractmanagement.booking.inbound.graphql

/**
 * GraphQL payload for UC01 — RequestTourBooking.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 9.
 */
data class RequestTourBookingPayload(
    val bookingId: String,
    val status: String,
)
