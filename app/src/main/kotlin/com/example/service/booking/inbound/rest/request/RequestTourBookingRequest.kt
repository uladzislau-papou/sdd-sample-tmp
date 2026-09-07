package com.example.service.booking.inbound.rest.request

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

/**
 * REST request body for UC01 — RequestTourBooking.
 *
 * Bean Validation enforces the **syntactic** rules at the HTTP boundary. Semantic rules —
 * the date being in the future, the capacity fitting — are enforced by the domain, and
 * deliberately not duplicated here: a rule in two places drifts, and only one of the two
 * copies runs when the use case is driven by something other than HTTP.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 2.
 */
data class RequestTourBookingRequest(
    @field:NotBlank val tourId: String,
    val tourDate: LocalDate,
    @field:Min(1) val participantCount: Int,
    @field:NotBlank val contactName: String,
    @field:NotBlank val contactEmail: String,
)
