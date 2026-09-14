package com.jobradleasing.contractmanagement.shared.outport

import com.jobradleasing.contractmanagement.shared.domain.event.DomainEvent

/**
 * The outbound port through which drivers hand domain events off for
 * post-commit delivery.
 *
 * SDD: See `documentation/ports/domain-event-publisher.outport.spec.md`.
 */
interface DomainEventPublisher {
    /**
     * Schedules [event] for delivery after the active transaction commits.
     * Nothing is delivered if the transaction rolls back. The caller MUST be
     * inside a transaction.
     */
    fun publish(event: DomainEvent)
}
