package com.example.contractmanagement.guide.inbound.rest

import com.example.contractmanagement.guide.core.domain.guidetour.exception.GuideTourNotFoundException
import com.example.contractmanagement.guide.core.domain.guidetour.exception.InvalidGuideTourStateException
import com.example.contractmanagement.guide.core.domain.guidetour.exception.TourStartTooEarlyException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Translates the `guide` context's exceptions into HTTP responses.
 *
 * Separate from the booking context's handler on purpose: each context owns the mapping of
 * its own vocabulary, and a shared handler would need to import both contexts' exception
 * types — which `ContextRegistryTest` forbids.
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md` § 3.
 */
@RestControllerAdvice
class GuideExceptionHandler {
    @ExceptionHandler(GuideTourNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleGuideTourNotFound(ex: GuideTourNotFoundException) = ErrorResponse(ex.message)

    @ExceptionHandler(InvalidGuideTourStateException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleInvalidGuideTourState(ex: InvalidGuideTourStateException) = ErrorResponse(ex.message)

    @ExceptionHandler(TourStartTooEarlyException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleTourStartTooEarly(ex: TourStartTooEarlyException) = ErrorResponse(ex.message)

    /** Backstop for identifier parsing on a path variable. See the booking handler. */
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleIllegalArgument(ex: IllegalArgumentException) = ErrorResponse(ex.message)

    data class ErrorResponse(
        val error: String?,
    )
}
