package com.dominikgaller.alpinebooking.booking.core.outport;

import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;

/**
 * Outbound port for handing off domain events after a use case transaction completes.
 *
 * <p>Must be called within an active transaction. The implementation is responsible for
 * delivering the event after commit (see ADR 0002 for the post-commit strategy).
 *
 * <p>The interface is framework-free. The Spring {@code ApplicationEventPublisher} integration
 * lives entirely in the adapter ({@code outbound.integration.LoggingDomainEventPublisher}).
 *
 * <p>SDD: See {@code documentation/ports/domain-event-publisher.outport.spec.md}
 *          and {@code documentation/adr/0002-domain-event-publication.adr.md}.
 */
public interface DomainEventPublisher {

    /**
     * Hands off the given event for post-commit delivery.
     *
     * @param event the domain event to publish; must not be null
     */
    void publish(TourBookingRequested event);
}
