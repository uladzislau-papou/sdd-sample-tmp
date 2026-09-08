package com.example.contractmanagement.booking.core.inport.command

import java.time.LocalDate

/**
 * Input for the RequestTourBooking use case (UC01).
 *
 * Carries only standard-library types — no domain value objects. Structural validation
 * happens when the driver maps this command onto domain types, so the domain remains the
 * guard even if a future caller has no adapter in front of it
 * (`coding-style.definition.md` § 6.2).
 *
 * SDD: see `documentation/ports/request-tour-booking.inport.spec.md` § 2.1.
 */
data class RequestTourBookingCommand(
    val tourId: String,
    val tourDate: LocalDate,
    val participantCount: Int,
    val contactName: String,
    val contactEmail: String,
)
