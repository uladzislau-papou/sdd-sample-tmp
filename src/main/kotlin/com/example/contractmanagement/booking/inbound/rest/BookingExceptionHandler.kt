package com.example.contractmanagement.booking.inbound.rest

import com.example.contractmanagement.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates domain and infrastructure exceptions into HTTP responses for the `booking`
 * context. Every handler answers `{ "error": "<message>" }`.
 *
 * SDD: see § 3 of the booking use-case specs.
 */
@RestControllerAdvice
class BookingExceptionHandler {
    @ExceptionHandler(InvalidBookingRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidBookingRequest(ex: InvalidBookingRequestException) = ErrorResponse(ex.message)

    /**
     * Backstop for input that reaches the domain as an [IllegalArgumentException] instead
     * of a domain exception.
     *
     * Two sources: parsing an identifier out of a path variable, and shared-kernel value
     * objects, which cannot reference a bounded context's exception type because
     * `architecture.definition.md` § 9 forbids `shared` from depending on a context — and
     * `ContextRegistryTest` enforces it. Both are client errors, and both surfaced as 500
     * before this mapping existed.
     *
     * A backstop, not a licence: a value object built from a command must throw a domain
     * exception (`coding-style.definition.md` § 6.2).
     */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException) = ErrorResponse(ex.message)

    @ExceptionHandler(CapacityExceededException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleCapacityExceeded(ex: CapacityExceededException) = ErrorResponse(ex.message)

    @ExceptionHandler(AvailabilityUnavailableException::class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    fun handleAvailabilityUnavailable(ex: AvailabilityUnavailableException) = ErrorResponse(ex.message)

    @ExceptionHandler(BookingNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleBookingNotFound(ex: BookingNotFoundException) = ErrorResponse(ex.message)

    @ExceptionHandler(InvalidBookingStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInvalidBookingState(ex: InvalidBookingStateException) = ErrorResponse(ex.message)

    data class ErrorResponse(
        val error: String?,
    )
}
