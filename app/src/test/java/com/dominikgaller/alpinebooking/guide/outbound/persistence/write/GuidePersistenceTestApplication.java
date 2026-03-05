package com.dominikgaller.alpinebooking.guide.outbound.persistence.write;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application used exclusively by guideoperations persistence integration tests.
 *
 * <p>Scopes component scanning to the {@code guideoperations.outbound.persistence.write} package so
 * the full application bootstrap (REST adapter, stubs, etc.) is not required.
 *
 * <p>Auto-configures DataSource, Flyway, jOOQ and picks up {@link GuideTourJooqRepository}.
 */
@SpringBootApplication(
        scanBasePackages = "com.dominikgaller.alpinebooking.guide.outbound.persistence.write"
)
class GuidePersistenceTestApplication {
}
