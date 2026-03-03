package com.dominikgaller.alpinebooking.booking.bootstrap;

import com.dominikgaller.alpinebooking.booking.outbound.integration.LoggingDomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.outbound.integration.StubAvailabilityChecker;
import com.dominikgaller.alpinebooking.booking.outbound.integration.clock.SystemClockPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring configuration for the booking bounded context.
 *
 * <p>Declares beans for outport adapter implementations. No business logic here;
 * only construction and injection of collaborators.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md}, section 4.9.
 */
@Configuration
public class BookingConfig {

    @Bean
    public StubAvailabilityChecker availabilityChecker() {
        return new StubAvailabilityChecker();
    }

    @Bean
    public LoggingDomainEventPublisher domainEventPublisher(
            final ApplicationEventPublisher applicationEventPublisher) {
        return new LoggingDomainEventPublisher(applicationEventPublisher);
    }

    @Bean
    public SystemClockPort clockPort() {
        return new SystemClockPort();
    }
}
