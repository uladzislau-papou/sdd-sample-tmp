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
- Java 21 (project baseline)

### Framework
- Spring Boot 4.x
- Built on Spring Framework 7
- Supports modern Gradle versions (incl. Gradle 9)

### Build Tool
- Gradle (preferred: Gradle 9; Gradle 8.14+ acceptable, but not verified)
- Use Gradle Wrapper (recommended)￼

### Database & Migration
- H2 for simplicity and local dev (in-memory and/or file mode)
- Flyway for schema migrations
- H2 is supported; version alignment matters in practice  ￼

### SQL Access
- jOOQ (type-safe SQL)
- Java 21 supported (including OSS edition)  ￼

### Testing
- JUnit 5 (via Spring Boot test support)
- AssertJ as the assertion library (mandatory for assertions)


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
- jOOQ version selection MUST be compatible with Java 21.  ￼


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

Principles
- Tests are part of the spec enforcement.
- Tests must express domain intent clearly.

Mandatory Rules
- AssertJ MUST be used for assertions.
- Tests must cover:
  - Domain invariants (Always-Valid)
  - Use case behavior (happy path + failure scenarios)
  - Persistence adapter behavior (integration tests)

Test Types 
1. Domain Tests (fast, no Spring)
   - Pure Java tests
   - Verify invariants and state transitions 
2. Use Case Tests (lightweight Spring if needed)
   - Validate orchestration and port interaction 
3. Adapter Integration Tests
   - Run with H2
   - Flyway migrations applied
   - Validate jOOQ queries + mapping


## Build & Quality Gates

Mandatory Commands
- ./gradlew clean test
- ./gradlew build

Must Hold
- All tests pass
- No task completes without passing test and build
- No “ignored” or placeholder tests


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