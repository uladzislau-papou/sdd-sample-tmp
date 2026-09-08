package com.example.contractmanagement.booking.inbound.rest

import com.example.contractmanagement.booking.inbound.rest.request.RequestTourBookingRequest
import com.example.contractmanagement.booking.inbound.rest.response.ConfirmTourBookingResponse
import com.example.contractmanagement.booking.inbound.rest.response.RequestTourBookingResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

/**
 * Inbound REST contract for tour booking operations.
 *
 * Holds the entire HTTP surface — routes, methods, status codes, parameter binding — so
 * that the adapter implementing it ([TourBookingRestController]) carries no HTTP
 * annotation at all. The split means the wire contract can be read in one file, and it is
 * enforced: `ClassRoleRulesTest` fails a `*RestController` that carries an MVC annotation.
 *
 * This is **not** the inbound port. The inbound port is `core.inport.usecase.*UseCase`
 * (`coding-style.definition.md` § 3.3).
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` and
 * `documentation/use-cases/uc02-confirm-tour-booking.spec.md`.
 */
@RequestMapping("/api/v1/bookings")
interface TourBookingRestAPI {
    /** UC01 — creates a booking request. Returns 201 with the new identity and status. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun request(
        @Valid @RequestBody request: RequestTourBookingRequest,
    ): RequestTourBookingResponse

    /** UC02 — confirms an existing booking. Returns 200 with the updated status. */
    @PostMapping("/{bookingId}/confirm")
    fun confirm(
        @PathVariable bookingId: String,
    ): ConfirmTourBookingResponse
}
