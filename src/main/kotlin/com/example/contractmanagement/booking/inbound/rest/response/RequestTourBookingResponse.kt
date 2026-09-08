package com.example.contractmanagement.booking.inbound.rest.response

/**
 * REST response body for UC01 — RequestTourBooking.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 9.
 */
data class RequestTourBookingResponse(
    val bookingId: String,
    val status: String,
)
