package com.dominikgaller.alpinebooking.guide.inbound.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot configuration used exclusively by the guide web slice tests.
 *
 * <p>Needed because {@code @WebMvcTest} searches <em>upwards</em> from the test's package
 * for a {@code @SpringBootConfiguration}, and {@link
 * com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplication} lives in {@code
 * bootstrap} — a sibling of {@code guide}, not an ancestor. That placement is deliberate
 * (see {@code documentation/architecture.definition.md} section 4.9: bootstrap sits outside
 * every bounded-context package), so each slice supplies its own configuration.
 *
 * <p>Mirrors {@code GuidePersistenceTestApplication}, which solves the same problem for the
 * persistence integration tests.
 *
 * <p>Scanning is scoped to {@code guide.inbound.rest} so only the web adapter and its
 * {@code @RestControllerAdvice} are candidates. Under {@code @WebMvcTest} the
 * auto-configuration is sliced to web concerns, so no DataSource, Flyway or jOOQ is created.
 */
@SpringBootApplication(
        scanBasePackages = "com.dominikgaller.alpinebooking.guide.inbound.rest"
)
class GuideWebTestApplication {
}
