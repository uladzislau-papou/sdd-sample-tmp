package com.jobradleasing.contractmanagement.shared.outport

import com.jobradleasing.contractmanagement.shared.domain.event.DomainEvent

/** Test double recording every published event, for driver-test assertions. */
class RecordingDomainEventPublisher : DomainEventPublisher {
    val published: MutableList<DomainEvent> = mutableListOf()

    override fun publish(event: DomainEvent) {
        published.add(event)
    }
}
