package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application used exclusively by persistence integration tests.
 *
 * <p>Scopes component scanning to the {@code outbound.persistence.write} package so
 * the full application bootstrap (REST adapter, stubs, etc.) is not required in Phase 6.
 *
 * <p>Auto-configures DataSource, Flyway, jOOQ and picks up {@link TourBookingJooqRepository}.
 */
@SpringBootApplication(
        scanBasePackages = "com.dominikgaller.alpinebooking.booking.outbound.persistence.write"
)
class PersistenceTestApplication {
}
