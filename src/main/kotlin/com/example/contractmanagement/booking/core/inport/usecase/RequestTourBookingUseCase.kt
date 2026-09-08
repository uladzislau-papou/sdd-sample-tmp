package com.example.contractmanagement.booking.core.inport.usecase

import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.inport.result.RequestTourBookingResult

/**
 * Inbound port for UC01 — RequestTourBooking.
 *
 * The only entry point for creating a booking from outside the core. Framework-free: no
 * Spring, no Jakarta annotations.
 *
 * The transaction boundary belongs to the driver. A caller MUST NOT wrap this in its own
 * transaction — doing so would put an adapter in charge of a domain guarantee.
 *
 * SDD: see `documentation/ports/request-tour-booking.inport.spec.md`.
 */
interface RequestTourBookingUseCase {
    fun request(command: RequestTourBookingCommand): RequestTourBookingResult
}
