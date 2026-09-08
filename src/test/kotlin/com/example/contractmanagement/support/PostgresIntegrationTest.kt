package com.example.contractmanagement.support

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for adapter integration tests that need a real database.
 *
 * The container is `companion object` state, so JUnit starts **one** PostgreSQL for the
 * whole run rather than one per test class, and Spring's context cache keeps the
 * connection details stable across classes.
 *
 * PostgreSQL, not an in-memory engine, on purpose: an integration test that proves a
 * migration applies and a query returns on H2 proves nothing about production
 * (`test.definition.md`, Integration Tests). The image tag is pinned for the same reason
 * the JDK toolchain is — a floating tag makes the test depend on when it ran.
 *
 * Note for the architecture tests: this package is test-only support and is not a bounded
 * context. `ContextRegistryTest` must therefore import main classes only.
 *
 * Two detekt rules pull this class in opposite directions, and neither is wrong about what
 * it sees. `UtilityClassWithPublicConstructor` objected to an abstract class exposing a
 * public constructor, which is why the constructor is `protected`;
 * `AbstractClassCanBeInterface` then objected that a class with no concrete member should be
 * an interface. It cannot be one: the shared container has to be static, and Kotlin does not
 * permit `@JvmStatic` in an interface's companion object. So the second rule is suppressed
 * with its reason rather than obeyed.
 */
@Suppress("AbstractClassCanBeInterface")
@Testcontainers
abstract class PostgresIntegrationTest protected constructor() {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
    }
}
