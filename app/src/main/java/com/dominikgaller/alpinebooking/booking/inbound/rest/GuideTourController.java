package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.StartTourUseCase;
import com.dominikgaller.alpinebooking.booking.inbound.rest.request.StartTourRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.StartTourResponse;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Web adapter implementing {@link GuideTourRestAPI}.
 *
 * <p>Maps request DTOs to use case commands, delegates to the relevant inport,
 * and maps results to response DTOs. Contains no HTTP annotations, no domain
 * logic, and no error handling — those concerns belong to {@link GuideTourRestAPI}
 * and {@link BookingExceptionHandler} respectively.
 */
@RestController
public class GuideTourController implements GuideTourRestAPI {

    private final StartTourUseCase startTourUseCase;

    public GuideTourController(final StartTourUseCase startTourUseCase) {
        this.startTourUseCase = startTourUseCase;
    }

    @Override
    public StartTourResponse start(final String guideTourId, final StartTourRequest request) {
        final var startedAt = Optional.ofNullable(request)
                .map(StartTourRequest::startedAt);
        final var result = startTourUseCase.start(new StartTourCommand(guideTourId, startedAt));
        return new StartTourResponse(result.status());
    }
}
