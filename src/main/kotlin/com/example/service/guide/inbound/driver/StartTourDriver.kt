package com.example.service.guide.inbound.driver

import com.example.service.guide.core.domain.guidetour.GuideTourId
import com.example.service.guide.core.domain.guidetour.exception.GuideTourNotFoundException
import com.example.service.guide.core.inport.command.StartTourCommand
import com.example.service.guide.core.inport.result.StartTourResult
import com.example.service.guide.core.inport.usecase.StartTourUseCase
import com.example.service.guide.core.outport.GuideTourRepository
import com.example.service.shared.outport.ClockPort
import com.example.service.shared.outport.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Application service implementing UC05 — StartTour.
 *
 * Owns the transaction boundary. The `TourStarted` event it hands off is what the
 * `booking` context reacts to in UC06 — the only link between the two contexts, and the
 * reason neither imports the other.
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md`.
 */
@Service
@Transactional
class StartTourDriver(
    private val guideTourRepository: GuideTourRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : StartTourUseCase {
    override fun start(command: StartTourCommand): StartTourResult {
        val effectiveStart = command.startedAt ?: clockPort.now()
        val guideTourId = GuideTourId(UUID.fromString(command.guideTourId))

        val guideTour =
            guideTourRepository.findById(guideTourId)
                ?: throw GuideTourNotFoundException(command.guideTourId)

        guideTour.start(effectiveStart)

        guideTourRepository.update(guideTour)
        guideTour.pullDomainEvents().forEach(domainEventPublisher::publish)

        return StartTourResult(guideTour.status.name)
    }
}
