package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.core.inport.command.CompleteTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CompleteTourUseCase;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.StartTourUseCase;
import com.dominikgaller.alpinebooking.guide.inbound.rest.request.CompleteTourRequest;
import com.dominikgaller.alpinebooking.guide.inbound.rest.request.StartTourRequest;
import com.dominikgaller.alpinebooking.guide.inbound.rest.response.CompleteTourResponse;
import com.dominikgaller.alpinebooking.guide.inbound.rest.response.StartTourResponse;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Web adapter implementing {@link GuideTourRestAPI}.
 *
 * <p>Maps request DTOs to use case commands, delegates to the relevant inport,
 * and maps results to response DTOs. Contains no HTTP annotations, no domain
 * logic, and no error handling — those concerns belong to {@link GuideTourRestAPI}
 * and {@link GuideExceptionHandler} respectively.
 */
@RestController
public class GuideTourController implements GuideTourRestAPI {

    private final StartTourUseCase startTourUseCase;
    private final CompleteTourUseCase completeTourUseCase;

    public GuideTourController(
            final StartTourUseCase startTourUseCase,
            final CompleteTourUseCase completeTourUseCase) {
        this.startTourUseCase = startTourUseCase;
        this.completeTourUseCase = completeTourUseCase;
    }

    @Override
    public StartTourResponse start(final String guideTourId, final StartTourRequest request) {
        final var startedAt = Optional.ofNullable(request)
                .map(StartTourRequest::startedAt);
        final var result = startTourUseCase.start(new StartTourCommand(guideTourId, startedAt));
        return new StartTourResponse(result.status());
    }

    @Override
    public CompleteTourResponse complete(
            final String guideTourId, final CompleteTourRequest request) {
        final var completedAt = Optional.ofNullable(request)
                .map(CompleteTourRequest::completedAt);
        final var result = completeTourUseCase.complete(
                new CompleteTourCommand(guideTourId, completedAt));
        return new CompleteTourResponse(result.status());
    }
}
