package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidCancellationReasonException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
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
public class GuideExceptionHandler {

    @ExceptionHandler(GuideTourNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleGuideTourNotFound(final GuideTourNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(TourCompletedBeforeStartException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleTourCompletedBeforeStart(
            final TourCompletedBeforeStartException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * Backstop for malformed input reaching the domain as an
     * {@code IllegalArgumentException} — chiefly {@code UUID.fromString} on the
     * {@code guideTourId} path variable in {@code StartTourDriver}. A client error, and a
     * 500 before this mapping existed.
     *
     * <p>See {@code ports/start-tour.inport.spec.md} section 7 and
     * {@code coding-style.definition.md} section 6.2.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(final IllegalArgumentException ex) {
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

    /**
     * UC12 — the booking side did not complete, so the tour cancellation was rolled back.
     *
     * <p>502 rather than 500 deliberately: the guide's request was well-formed and its tour
     * was cancellable, so this is a downstream failure the caller may reasonably retry.
     * Collapsing it into a 500 would tell them to give up.
     */
    @ExceptionHandler(BookingCancellationFailedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleBookingCancellationFailed(
            final BookingCancellationFailedException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * UC12 — a blank or over-long cancellation reason. A domain exception rather than the
     * {@code IllegalArgumentException} backstop, because it is a business rule this context
     * owns about a value it stores ({@code coding-style.definition.md} § 6.2 clause A).
     */
    @ExceptionHandler(InvalidCancellationReasonException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidCancellationReason(
            final InvalidCancellationReasonException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String error) {
    }
}
