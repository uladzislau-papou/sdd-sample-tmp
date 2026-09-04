# ADR 0001 – Technical Stack

## Status

Accepted (retroactive)

This ADR records a decision already embodied in `build.gradle.kts`. It was reconstructed
when the SDD documentation set was adapted to this service; the reasoning below is
inferred from the code and the README, not from a contemporaneous deliberation.

## Context

Risk Management Service is a backend for regulated workflows — identity verification,
qualified electronic signatures, and KYC. It must:

- expose a stable API to several internal product backends,
- integrate with four external providers over HTTP,
- persist regulated state durably and auditably,
- run on the platform's existing JVM deployment substrate.

The team's existing competence and the platform's tooling are both JVM-centric.

## Decision

| Concern | Choice | Version |
|---------|--------|---------|
| Language | Kotlin | 2.3.0 |
| JVM toolchain and target | Java | 17 |
| Framework | Spring Boot | 4.0.3 (Spring Framework 7) |
| Web | `spring-boot-starter-web` (servlet) | — |
| API | `spring-boot-starter-graphql` | — |
| Outbound HTTP | `spring-boot-starter-restclient` | — |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate) | — |
| Database | PostgreSQL | — |
| Migrations | Flyway | via `spring-boot-starter-flyway` |
| State machine | `spring-statemachine-core` | 4.0.1 |
| JSON | `jackson-module-kotlin` | 3.0.0 |
| Logging | `kotlin-logging-jvm` | 6.0.0 |
| Metrics | Micrometer (OTLP + Prometheus) | — |
| Build | Gradle with wrapper, single module | — |
| Format | Spotless + ktlint | 1.5.0 |
| Static analysis | detekt | 2.0.0-alpha.2 |
| Coverage | JaCoCo (report only) | 0.8.13 |
| Tests | JUnit 5, mockito-kotlin, AssertJ, GraphQlTester | — |
| API e2e | Playwright | — |

Compiler settings: `-Xjsr305=strict` and `allWarningsAsErrors = true`.

Kotlin compiler plugins `plugin.spring` (all-open) and `plugin.jpa` (no-arg) are applied,
because Kotlin classes are final and constructor-only by default and both Spring proxying
and Hibernate need otherwise.

## Consequences

**Positive**

- One language, one framework, one datastore — a small surface for a service with a large
  feature footprint.
- `allWarningsAsErrors` makes deprecation and nullability drift a build failure rather
  than a slow accumulation.
- Spring Boot 4 / Framework 7 keeps the service current with the platform's support
  window.

**Negative / accepted**

- **JVM 17 with Spring Boot 4** is a conservative pairing; the runtime is several LTS
  releases behind the language and framework. Raising it is trigger 13.
- `detekt 2.0.0-alpha.2` is a pre-release on a merge-blocking gate. Accepted for its rule
  set; a regression in the tool blocks the build. Revisit when 2.0.0 is final.
- `allWarningsAsErrors` occasionally forces a `@Suppress` at a genuine dead end.
  `coding-style.definition.md` § 1.4 requires such a suppression to be justified inline.
- Hibernate's reflective instantiation is what makes always-valid construction impossible;
  see ADR 0003.

**Constraints this imposes**

- Any change to a row of the table above is an ADR trigger
  (`sdd.playbook.md` § 6 items 2 and 13).
- `build.gradle.kts` is the single authoritative version declaration. No version is
  duplicated into documentation as a fact — only as a reference.

## Alternatives considered

- **Java instead of Kotlin.** Rejected in effect: null-safety, data classes and exhaustive
  `when` do real work in a codebase whose main hazard is unmapped provider values.
- **A reactive stack (WebFlux, R2DBC).** Rejected. The workload is provider-latency-bound,
  not connection-bound, and the operational cost of reactive debugging is not repaid.
- **A second datastore for documents or events.** Rejected. PostgreSQL with JSON columns
  and outbox tables covers it; see ADR 0008 and ADR 0009.
