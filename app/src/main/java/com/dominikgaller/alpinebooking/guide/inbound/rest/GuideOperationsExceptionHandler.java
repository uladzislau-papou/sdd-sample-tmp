package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates guide domain exceptions into HTTP error responses.
 *
 * <p>Each handler returns {@code { "error": "<message>" }} with the appropriate HTTP status.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 3.
 */
@RestControllerAdvice
public class GuideOperationsExceptionHandler {

    @ExceptionHandler(GuideTourNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleGuideTourNotFound(final GuideTourNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InvalidGuideTourStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidGuideTourState(final InvalidGuideTourStateException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(TourStartTooEarlyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTourStartTooEarly(final TourStartTooEarlyException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String error) {
    }
}
