package com.example.service.booking.core.inport.result

/**
 * Output of the ConfirmTourBooking use case (UC02).
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md` § 3.
 */
data class ConfirmTourBookingResult(
    val status: String,
)
