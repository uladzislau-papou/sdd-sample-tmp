package com.dominikgaller.alpinebooking.bootstrap;

import com.dominikgaller.alpinebooking.shared.outbound.clock.SystemClockPort;
import com.dominikgaller.alpinebooking.shared.outbound.integration.LoggingDomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the shared kernel's outbound adapters.
 *
 * <p>These beans implement {@code shared.outport} interfaces, which more than one bounded
 * context depends on and none of them owns. They are declared here rather than in a
 * per-context config so that no context has to obtain a context-neutral bean from
 * another context's wiring.
 *
 * <p>Previously {@code ClockPort} and {@code DomainEventPublisher} were declared in
 * {@code BookingConfig}, which meant the {@code guide} context got its clock from
 * {@code booking}'s configuration. No import crossed a context boundary, so the
 * dependency was invisible to a compile-time check — but it made ADR 0003's claim that
 * "both contexts depend on {@code shared.domain} only" false.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md} section 9 (Shared Kernel)
 *          and section 4.9 (bootstrap).
 */
@Configuration
public class SharedConfig {

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
