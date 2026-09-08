package com.example.contractmanagement.booking.core.inport.result

/**
 * Output of the RequestTourBooking use case (UC01).
 *
 * SDD: see `documentation/ports/request-tour-booking.inport.spec.md` § 2.2.
 */
data class RequestTourBookingResult(
    val bookingId: String,
    val status: String,
)
