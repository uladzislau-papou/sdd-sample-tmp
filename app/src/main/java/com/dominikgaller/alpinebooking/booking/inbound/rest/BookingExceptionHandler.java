package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain and infrastructure exceptions into HTTP error responses.
 *
 * <p>Each handler returns {@code { "error": "<message>" }} with the appropriate HTTP status.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}, section 3,
 *          and {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}, section 3.
 */
@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(InvalidBookingRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidBookingRequest(final InvalidBookingRequestException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * Backstop for malformed input that reaches the domain as an
     * {@code IllegalArgumentException} rather than a domain exception.
     *
     * <p>Two sources today: {@code UUID.fromString} on a path variable in every driver,
     * and value objects whose guards still throw it ({@code TourId}). Both are client
     * errors, and both surfaced as 500 before this mapping existed.
     *
     * <p>This is a backstop, not a licence. A value object built from a command should
     * throw a domain exception — see {@code coding-style.definition.md} section 6.2. The
     * mapping exists because {@code TourId} lives in {@code shared.domain} and therefore
     * cannot reference a bounded context's exception type.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(final IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(CapacityExceededException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCapacityExceeded(final CapacityExceededException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(AvailabilityUnavailableException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleAvailabilityUnavailable(final AvailabilityUnavailableException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleBookingNotFound(final BookingNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInvalidBookingState(final InvalidBookingStateException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    public record ErrorResponse(String error) {
    }
}
