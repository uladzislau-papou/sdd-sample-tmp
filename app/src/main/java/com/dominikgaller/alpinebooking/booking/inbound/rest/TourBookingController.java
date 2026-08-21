package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.inbound.rest.request.CancelTourBookingRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.request.ChangeParticipantsRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.request.RequestTourBookingRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.CancelTourBookingResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.ChangeParticipantsResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.ConfirmTourBookingResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.RequestTourBookingResponse;
import com.dominikgaller.alpinebooking.booking.core.inport.command.CancelTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ChangeParticipantsCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ConfirmTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.command.RequestTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.CancelTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.ChangeParticipantsUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.ConfirmTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.RequestTourBookingUseCase;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web adapter implementing {@link TourBookingRestAPI}.
 *
 * <p>Maps request DTOs to use case commands, delegates to the relevant inport,
 * and maps results to response DTOs. Contains no HTTP annotations, no domain
 * logic, and no error handling — those concerns belong to {@link TourBookingRestAPI}
 * and {@link BookingExceptionHandler} respectively.
 */
@RestController
public class TourBookingController implements TourBookingRestAPI {

    private final RequestTourBookingUseCase requestTourBookingUseCase;
    private final ConfirmTourBookingUseCase confirmTourBookingUseCase;
    private final CancelTourBookingUseCase cancelTourBookingUseCase;
    private final ChangeParticipantsUseCase changeParticipantsUseCase;

    public TourBookingController(
            final RequestTourBookingUseCase requestTourBookingUseCase,
            final ConfirmTourBookingUseCase confirmTourBookingUseCase,
            final CancelTourBookingUseCase cancelTourBookingUseCase,
            final ChangeParticipantsUseCase changeParticipantsUseCase) {
        this.requestTourBookingUseCase = requestTourBookingUseCase;
        this.confirmTourBookingUseCase = confirmTourBookingUseCase;
        this.cancelTourBookingUseCase = cancelTourBookingUseCase;
        this.changeParticipantsUseCase = changeParticipantsUseCase;
    }

    @Override
    public RequestTourBookingResponse request(final RequestTourBookingRequest request) {
        final var result = requestTourBookingUseCase.request(new RequestTourBookingCommand(
                request.tourId(),
                request.tourDate(),
                request.participantCount(),
                request.contactName(),
                request.contactEmail()));
        return new RequestTourBookingResponse(result.bookingId(), result.status());
    }

    @Override
    public ConfirmTourBookingResponse confirm(final String bookingId) {
        final var result = confirmTourBookingUseCase.confirm(new ConfirmTourBookingCommand(bookingId));
        return new ConfirmTourBookingResponse(result.status());
    }

    @Override
    public CancelTourBookingResponse cancel(
            final String bookingId,
            final CancelTourBookingRequest request) {
        // A absent body is equivalent to one with both fields null: cancelling without a
        // reason is permitted, so the controller normalises rather than rejects.
        final var result = cancelTourBookingUseCase.cancel(new CancelTourBookingCommand(
                bookingId,
                request == null ? null : request.cancelledAt(),
                request == null ? null : request.reason()));
        return new CancelTourBookingResponse(result.status());
    }

    @Override
    public ChangeParticipantsResponse changeParticipants(
            final String bookingId,
            final ChangeParticipantsRequest request) {
        final var result = changeParticipantsUseCase.change(
                new ChangeParticipantsCommand(bookingId, request.newParticipantCount()));
        return new ChangeParticipantsResponse(result.participantCount());
    }

}
