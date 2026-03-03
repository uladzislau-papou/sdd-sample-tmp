package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for UC01 – RequestTourBooking.
 *
 * <p>Delegates entirely to {@link RequestTourBookingUseCase}. Contains no domain logic:
 * only maps the request DTO to a command, invokes the use case, and maps the result to
 * a response DTO.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}, section 9.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class TourBookingController {

    private final RequestTourBookingUseCase useCase;

    public TourBookingController(final RequestTourBookingUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Creates a new tour booking request.
     *
     * @param request validated request body
     * @return HTTP 201 with booking id and status
     */
    @PostMapping
    public ResponseEntity<RequestTourBookingResponse> request(
            @Valid @RequestBody final RequestTourBookingRequest request) {

        final RequestTourBookingCommand command = new RequestTourBookingCommand(
                request.tourId(),
                request.tourDate(),
                request.participantCount(),
                request.contactName(),
                request.contactEmail());

        final RequestTourBookingResult result = useCase.request(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RequestTourBookingResponse(result.bookingId(), result.status()));
    }
}
