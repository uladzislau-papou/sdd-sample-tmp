package com.dominikgaller.alpinebooking.booking.outbound.integration;

import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.outport.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Delegates domain event publication to Spring's {@link ApplicationEventPublisher}.
 *
 * <p>The event is published within the active transaction so that
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} listeners receive it
 * only after the transaction has committed successfully (see ADR 0002).
 *
 * <p>SDD: See {@code documentation/ports/domain-event-publisher.outport.spec.md}
 *          and {@code documentation/adr/0002-domain-event-publication.adr.md}.
 */
public class LoggingDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public LoggingDomainEventPublisher(final ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(final TourBookingRequested event) {
        applicationEventPublisher.publishEvent(event);
    }
}
