package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.domain.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityUnavailableException;
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
