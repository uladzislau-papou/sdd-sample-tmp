# ADR 0009 – Kotlin / Spring Boot 4 / PostgreSQL Stack

## Status
Accepted (inherited from template)

Supersedes ADR-0001.

## Context

This repository became a reusable template for new services. The stack recorded in
ADR-0001 — Java 21, jOOQ, H2 — was chosen for a Java reference implementation and no longer
describes what is being built here.

The target stack was chosen to match a real service already in production
(`risk-management-service`), on the grounds that a template whose stack nobody runs is a
template nobody adopts.

## Decision

- **Kotlin 2.3** on **JVM 17** (ADR-0010)
- **Spring Boot 4**
- **Gradle 8.14**, Kotlin DSL, version catalog
- **REST and GraphQL** as delivery transports (ADR-0012)
- **Spring Data JPA** for persistence in this example, with the domain free of persistence
  annotations as the fixed rule (ADR-0011)
- **PostgreSQL** with **Flyway** migrations; **Testcontainers** for integration tests
  (ADR-0013)
- **ktlint via spotless**, **detekt**, `allWarningsAsErrors` (ADR-0014)

## Rationale

**Kotlin.** Nullability in the type system removes an entire category of rules the Java
style guide had to state in prose — `coding-style.definition.md` § 1.4 lost a rule *and*
its three-clause exception, because the exception existed only to work around Java's
inability to express a nullable field. `data class` and primary constructors remove the
boilerplate that annotation processors were used for. Sealed hierarchies with exhaustive
`when` make a new state a compile error rather than a silent fall-through.

**Spring Boot 4.** Same reasoning as ADR-0001: stable infrastructure wiring that does not
leak into the domain. Note one practical consequence — its test-slice annotations moved into
per-module artifacts, so `@DataJpaTest` and `@AutoConfigureTestDatabase` need
`spring-boot-data-jpa-test` and `spring-boot-jdbc-test` on the test classpath. The failure
mode is an unresolved import.

**What survived from ADR-0001 and what did not.** Its requirements for type safety, clear
boundaries, deterministic migrations and fast feedback all survived; Flyway survived
unchanged. Two things did not:

- **H2 was dropped.** ADR-0001 called it "sufficient for a reference implementation", which
  was true of a reference implementation and is not true of a template for real services.
  ADR-0013 records the replacement.
- **The explicit ban on JPA was reconsidered.** ADR-0001 forbade JPA to keep the domain
  clean. ADR-0011 argues that a technology ban is a weak control for that, names the
  guarantee directly, and enforces it with a rule that catches the *next* ORM too.

**Gradle 8.14 rather than the 9.3.1 already in use.** A deliberate downgrade, to match the
donor service. See ADR-0010, which records the same trade-off for the JDK.

## Consequences

- The jOOQ code-generation chain is gone, and with it the build-time dependency on a live
  database schema and roughly sixty lines of build script.
- Dropping the Flyway *Gradle plugin* — which existed only to feed that chain — allowed the
  Gradle configuration cache to be switched back on.
- Two delivery transports mean two adapters per exposed use case, and a class-role naming
  scheme that can tell them apart (ADR-0012).
