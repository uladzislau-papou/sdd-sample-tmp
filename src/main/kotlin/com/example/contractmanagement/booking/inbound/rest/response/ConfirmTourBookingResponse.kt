package com.example.contractmanagement.booking.inbound.rest.response

/**
 * REST response body for UC02 — ConfirmTourBooking.
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md` § 9.
 */
data class ConfirmTourBookingResponse(
    val status: String,
)
