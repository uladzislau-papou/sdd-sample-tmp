package com.example.contractmanagement.shared.outbound.integration

import com.example.contractmanagement.shared.domain.event.DomainEvent
import com.example.contractmanagement.shared.outport.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher

/**
 * Delegates domain event publication to Spring's [ApplicationEventPublisher].
 *
 * The event is published within the active transaction so that listeners annotated
 * `@TransactionalEventListener(phase = AFTER_COMMIT)` receive it only after the
 * transaction has committed successfully (ADR 0002).
 *
 * SDD: see `documentation/ports/domain-event-publisher.outport.spec.md`.
 */
class LoggingDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
