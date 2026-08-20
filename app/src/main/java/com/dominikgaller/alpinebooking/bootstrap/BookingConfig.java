package com.dominikgaller.alpinebooking.bootstrap;

import com.dominikgaller.alpinebooking.booking.outbound.integration.StubAvailabilityChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring configuration for the {@code booking} bounded context.
 *
 * <p>Declares beans for this context's own outport adapters only. Adapters implementing
 * {@code shared.outport} — {@code ClockPort}, {@code DomainEventPublisher} — are declared
 * in {@link SharedConfig}, because they belong to no single context and no context should
 * obtain them from another context's wiring
 * ({@code documentation/architecture.definition.md} section 9).
 *
 * <p>No business logic here; only construction and injection of collaborators.
 *
 * <p>SDD: See {@code documentation/architecture.definition.md}, section 4.9.
 */
@Configuration
public class BookingConfig {

    @Bean
    public StubAvailabilityChecker availabilityChecker() {
        return new StubAvailabilityChecker();
    }
}
