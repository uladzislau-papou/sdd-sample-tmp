package com.example.service.shared.outport

import com.example.service.shared.domain.event.DomainEvent

/**
 * Cross-context outbound port for handing off domain events after a use case transaction
 * completes.
 *
 * Must be called within an active transaction. The implementation is responsible for
 * delivering the event after commit (ADR 0002).
 *
 * The interface is framework-free; the Spring integration lives entirely in the adapter.
 *
 * SDD: see `documentation/ports/domain-event-publisher.outport.spec.md`.
 */
interface DomainEventPublisher {
    /** Hands off [event] for post-commit delivery. */
    fun publish(event: DomainEvent)
}
