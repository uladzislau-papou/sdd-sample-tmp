package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.inport.CancelTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.CancelTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.ConfirmTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.ConfirmTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.ConfirmTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter for tour booking use cases.
 *
 * <p>Delegates entirely to the relevant use case port. Contains no domain logic:
 * only maps request DTOs to commands, invokes the use case, and maps results to
 * response DTOs.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md},
 *          {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md},
 *          and {@code documentation/use-cases/uc03-cancle-tour-booking.spec.md}.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class TourBookingController {

    private final RequestTourBookingUseCase requestTourBookingUseCase;
    private final ConfirmTourBookingUseCase confirmTourBookingUseCase;
    private final CancelTourBookingUseCase cancelTourBookingUseCase;

    public TourBookingController(
            final RequestTourBookingUseCase requestTourBookingUseCase,
            final ConfirmTourBookingUseCase confirmTourBookingUseCase,
            final CancelTourBookingUseCase cancelTourBookingUseCase) {
        this.requestTourBookingUseCase = requestTourBookingUseCase;
        this.confirmTourBookingUseCase = confirmTourBookingUseCase;
        this.cancelTourBookingUseCase = cancelTourBookingUseCase;
    }

    /**
     * UC01 – Creates a new tour booking request.
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

        final RequestTourBookingResult result = requestTourBookingUseCase.request(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RequestTourBookingResponse(result.bookingId(), result.status()));
    }

    /**
     * UC02 – Confirms an existing tour booking.
     *
     * @param bookingId the UUID of the booking to confirm
     * @return HTTP 200 with updated status
     */
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<ConfirmTourBookingResponse> confirm(
            @PathVariable final String bookingId) {

        final ConfirmTourBookingResult result =
                confirmTourBookingUseCase.confirm(new ConfirmTourBookingCommand(bookingId));

        return ResponseEntity.ok(new ConfirmTourBookingResponse(result.status()));
    }

    /**
     * UC03 – Cancels an existing tour booking.
     *
     * @param bookingId the UUID of the booking to cancel
     * @return HTTP 200 with updated status
     */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<CancelTourBookingResponse> cancel(
            @PathVariable final String bookingId) {

        final CancelTourBookingResponse response = new CancelTourBookingResponse(
                cancelTourBookingUseCase.cancel(new CancelTourBookingCommand(bookingId)).status());

        return ResponseEntity.ok(response);
    }
}
