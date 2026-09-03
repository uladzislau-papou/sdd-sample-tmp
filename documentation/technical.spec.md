# Technical Specification

## Purpose

This document defines the technical baseline and implementation constraints for Alpine Booking.

Alpine Booking is a reference-grade backend system. 
The stack is intentionally opinionated to support:
- Domain-centric design (DDD)
- Hexagonal Architecture (Ports & Adapters)
- Spec Driven Development (SDD)
- Always-Valid domain modeling


## Tech Stack

### Language & Runtime
- Kotlin 2.4.10, JVM target 25 (LTS) — see `adr/0009-kotlin-migration.adr.md`.
  Supersedes the Java 25 baseline of `adr/0006-java-25-baseline.adr.md` with the same JVM
  target, changing only the source language.
  The Gradle toolchain in `gradle/libs.versions.toml` is the single authoritative
  declaration; `.sdkmanrc` and IDE settings are derived from it.
  The baseline tracks LTS releases only; moving it is an ADR trigger.

### Framework
- Spring Boot 4.x, with the `org.jetbrains.kotlin.plugin.spring` (all-open)
  plugin applied — Kotlin classes are final by default and Spring's proxying
  needs them open
- Built on Spring Framework 7
- Supports modern Gradle versions (incl. Gradle 9)
- `jackson-module-kotlin` on the classpath for Kotlin data class (de)serialization

### Build Tool
- Gradle (preferred: Gradle 9; Gradle 8.14+ acceptable, but not verified)
- Use Gradle Wrapper (recommended)￼

### Database & Migration
- H2 for simplicity and local dev (in-memory and/or file mode)
- Flyway for schema migrations
- H2 is supported; version alignment matters in practice  ￼

### SQL Access
- jOOQ (type-safe SQL)
- Code generation targets **Java**, not Kotlin, even though hand-written code is Kotlin —
  generated code is never hand-edited either way, and this keeps codegen unchanged from
  the prior baseline (`adr/0009-kotlin-migration.adr.md`)

### Testing
- JUnit 5 (via Spring Boot test support), written in Kotlin
- AssertJ as the assertion library (mandatory for assertions)
- ArchUnit for architecture enforcement (ADR 0007)

**ArchUnit MUST be 1.4.1 or newer.** Versions 1.3.0 and 1.4.0 cannot read Java 25 class
files (major version 69) and import **zero** classes — silently, with no error or warning.
This applies at the current JVM 25 target exactly as it did under the Java-25 baseline
ADR 0006 originally recorded it for; the class files carry the same major version
regardless of source language. Every architecture rule then checks nothing.
`ContextRegistryTest.importer_findsProductionClasses` asserts the import size directly
rather than trusting a green suite as evidence the importer worked.

This is a constraint the JVM baseline (`adr/0009-kotlin-migration.adr.md`) imposes on
tooling. Any future toolchain bump must re-verify that ArchUnit still reads the new
bytecode — a green architecture suite is not evidence that it does.


## Baseline Constraints

### Architectural Constraints
- The domain layer MUST remain framework-free (no Spring annotations, no JPA annotations, no JdbcTemplate, no jOOQ DSL types).
- All infrastructure dependencies live in adapters.
- Business rules live only in:
- Aggregates (preferred)
- Domain Services (only when cross-aggregate or non-entity logic is required)
- Application layer coordinates use cases and ports; it does not implement domain rules.

### Dependency Constraints
- No new dependency may be introduced without an ADR.
- Avoid “convenience” dependencies that leak abstraction boundaries.


## Persistence Strategy

### Database
- Primary dev DB: H2
- Mode:
- Default: in-memory for tests
- Optional: file mode for local debugging sessions

Note: H2 evolves quickly. Flyway compatibility can lag behind H2 releases and/or require version pinning.  ￼


### Migrations (Flyway)
- All schema changes MUST be expressed as Flyway migrations.
- Location:
- src/main/resources/db/migration
- Rules:
- No manual schema changes
- No “baseline-on-migrate” shortcuts unless explicitly justified in an ADR

SQL Access (jOOQ)
- jOOQ is the only supported SQL access strategy (no JPA/Hibernate).
- jOOQ code generation:
  - MUST be configured in Gradle
  - MUST run against the same schema that Flyway migrates
- jOOQ version selection MUST be compatible with the JVM 25 target.


### Flyway Migration Classification

To improve clarity and auditability, SQL migrations are categorized by intent.

Naming convention:

V<version>__<type>_<description>.sql

Where <type> is one of:

DDL – Data Definition Language
- CREATE TABLE
- ALTER TABLE
- DROP TABLE
- INDEX definitions
- Constraints

DML – Data Manipulation Language
- INSERT
- UPDATE
- DELETE
- Seed data

TCL – Transaction Control Language
- COMMIT
- ROLLBACK (rare, discouraged)

DCL – Data Control Language
- GRANT
- REVOKE

Example:

V1__DDL_init_schema.sql
V2__DML_seed_test_data.sql

Rules:

- DDL and DML MUST NOT be mixed in one migration.
- Seed data must be explicit DML migration.
- No implicit schema generation via ORM.

## Testing Strategy

This document specifies only the **tooling** side of testing (see `### Testing`
above: JUnit 5, AssertJ, H2, Flyway, jOOQ).

- Test taxonomy, assertion rules and coverage expectations →
  `test.definition.md`
- When in the cycle a test is written (RED-first) → `tdd.definition.md`

The taxonomy previously duplicated here has been removed under
`file-usage.definition.md` § 4.


## Build & Quality Gates

**Canonical list: `test.definition.md` § 7.**

This document owns only the *commands* those gates invoke, because the commands
are a property of the build tool:

```shell
./gradlew clean test
./gradlew build
```

Both are allowlisted in `.claude/settings.json` so agents can run them without
prompting.


## Local Development Defaults

Configuration
- Use Spring profiles:
- local for dev
- test for test runtime

Observability / Logging (baseline)
- Logging must not leak sensitive data.
- SQL logging can be enabled only for local debugging (not default).


## Non-Goals (Technical)
- No JPA/Hibernate
- No microservice split
- No reactive stack by default
- No “smart” frameworks inside the domain model