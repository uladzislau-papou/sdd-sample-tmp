package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.StartTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.StartTourUseCase;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC05 – StartTour use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, state transition,
 * persistence, and post-commit event publication without containing any domain rules.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
@Service
@Transactional
public class StartTourDriver implements StartTourUseCase {

    private final GuideTourRepository guideTourRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public StartTourDriver(
            final GuideTourRepository guideTourRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.guideTourRepository = guideTourRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public StartTourResult start(final StartTourCommand command) {
        final Instant effectiveStart = command.startedAt().orElseGet(clockPort::now);
        final GuideTourId guideTourId = new GuideTourId(UUID.fromString(command.guideTourId()));

        final GuideTour guideTour = guideTourRepository.findById(guideTourId)
                .orElseThrow(() -> new GuideTourNotFoundException(command.guideTourId()));

        guideTour.start(effectiveStart);

        guideTourRepository.update(guideTour);

        guideTour.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new StartTourResult(guideTour.status().name());
    }
}
