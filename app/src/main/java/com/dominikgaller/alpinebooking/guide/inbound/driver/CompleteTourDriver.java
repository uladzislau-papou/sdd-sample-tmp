package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CompleteTourCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CompleteTourResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CompleteTourUseCase;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC11 – CompleteTour use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, state transition,
 * persistence, and post-commit event publication without containing any domain rules —
 * the RUNNING guard and the completed-before-started check both live on {@link GuideTour}.
 *
 * <p>SDD: See {@code documentation/use-cases/uc11-complete-tour.spec.md}.
 */
@Service
@Transactional
public class CompleteTourDriver implements CompleteTourUseCase {

    private final GuideTourRepository guideTourRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public CompleteTourDriver(
            final GuideTourRepository guideTourRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.guideTourRepository = guideTourRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public CompleteTourResult complete(final CompleteTourCommand command) {
        final Instant effectiveCompletion = command.completedAt().orElseGet(clockPort::now);
        final GuideTourId guideTourId = new GuideTourId(UUID.fromString(command.guideTourId()));

        final GuideTour guideTour = guideTourRepository.findById(guideTourId)
                .orElseThrow(() -> new GuideTourNotFoundException(command.guideTourId()));

        guideTour.complete(effectiveCompletion);

        guideTourRepository.update(guideTour);

        guideTour.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new CompleteTourResult(guideTour.status().name());
    }
}
