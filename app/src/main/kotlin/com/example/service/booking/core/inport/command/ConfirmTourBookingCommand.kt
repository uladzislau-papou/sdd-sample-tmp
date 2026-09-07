package com.example.service.booking.core.inport.command

/**
 * Input for the ConfirmTourBooking use case (UC02).
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md` § 2.
 */
data class ConfirmTourBookingCommand(
    val bookingId: String,
)
