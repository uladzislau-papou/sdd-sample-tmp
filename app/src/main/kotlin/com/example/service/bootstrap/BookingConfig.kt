package com.example.service.bootstrap

import com.example.service.booking.outbound.integration.StubAvailabilityChecker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring configuration for the `booking` bounded context.
 *
 * Declares beans for this context's own outport adapters only. Adapters implementing
 * `shared.outport` are declared in [SharedConfig], because they belong to no single
 * context and no context should obtain them from another context's wiring.
 *
 * No business logic here; only construction and injection of collaborators.
 *
 * SDD: see `documentation/architecture.definition.md`, section 4.9.
 */
@Configuration
class BookingConfig {
    @Bean
    fun availabilityChecker() = StubAvailabilityChecker()
}
