package com.example.contractmanagement.booking.core.inport.usecase

import com.example.contractmanagement.booking.core.inport.command.ConfirmTourBookingCommand
import com.example.contractmanagement.booking.core.inport.result.ConfirmTourBookingResult

/**
 * Inbound port for UC02 — ConfirmTourBooking.
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md`.
 */
interface ConfirmTourBookingUseCase {
    fun confirm(command: ConfirmTourBookingCommand): ConfirmTourBookingResult
}
