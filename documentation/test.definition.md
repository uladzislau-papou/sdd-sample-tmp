# Test Definition – Alpine Booking (`test.definition.md`)

## Purpose

This document defines the testing strategy, rules, and quality gates for Alpine Booking.

Tests are not optional. They are the enforcement mechanism for:
- Always-Valid domain invariants
- Use case behavior
- Hexagonal architecture boundaries
- Persistence correctness (Flyway + jOOQ)


# 1. Tooling Baseline

## 1.1 Frameworks
- **JUnit 5** is the test runner baseline.
- **AssertJ** is mandatory for assertions.

## 1.2 Spring Boot
- Spring Boot test support MAY be used for:
    - adapter integration tests
    - web/API tests
    - wiring tests for application services

Domain tests SHOULD NOT require Spring.

## 1.3 Database for Tests
- **H2** is the default database for tests.
- **Flyway** MUST run migrations for any test that touches persistence.
- **jOOQ** is used to read/write database state in persistence adapter tests (no JPA/Hibernate).


# 2. Test Taxonomy (Required Types)

Tests are grouped by intent and architectural layer.

## 2.1 Domain Tests (Mandatory, Fast)
**Scope:** Domain layer only (Aggregates, Entities, Value Objects, Domain Services, Domain Events).

Rules:
- MUST NOT use Spring.
- MUST NOT hit the database.
- MUST express invariants and state transitions.
- MUST cover negative cases (invalid state transitions, invariant violations).

Examples of assertions (style guidance):
- Use AssertJ fluent assertions.
- Use `assertThatThrownBy(...)` for invariant failures.
- Prefer domain-specific value comparisons over technical ones.

## 2.2 Use Case Tests (Mandatory)
**Scope:** Application layer (Use Cases / Application Services) with ports mocked/stubbed.
ALways prefer good stubs before mocks.

Rules:
- SHOULD be Spring-free where possible.
- MAY use Spring only if wiring complexity is meaningful to validate.
- MUST validate:
    - orchestration logic (what ports are called and when)
    - emitted domain events (if applicable)
    - transactional expectations (conceptually, not by inspecting Spring internals)
    - failure scenarios (e.g., missing entity, invalid state)

## 2.3 Adapter Integration Tests (Mandatory when adapter changes)
**Scope:** Persistence adapters and infrastructure adapters.

For persistence (Flyway + jOOQ + H2):
- MUST run Flyway migrations.
- MUST verify roundtrip correctness:
    - write → read → domain equivalence (where mapping exists)
- MUST validate query semantics that matter for the domain:
    - uniqueness constraints
    - overlap queries (date ranges)
    - concurrency-relevant behavior where possible (at least idempotency)

Rules:
- MUST NOT test internal framework behavior.
- MUST test system behavior at adapter boundary.

## 2.4 API / Web Tests (As Needed)
**Scope:** Inbound adapters (REST controllers, messaging consumers).

Rules:
- MUST exist for:
    - externally exposed endpoints
    - request validation rules
    - HTTP status mapping
    - error response contract
- SHOULD use slice tests (e.g., MVC slice) when possible.
- MUST map each endpoint to a Use Case (traceability).


# 3. Assertion Rules (Strict)

## 3.1 AssertJ is Mandatory
- Do not use `assertEquals`, `assertTrue`, etc.
- Prefer AssertJ fluent style with meaningful failure messages.

## 3.2 Exception Expectations
- Invariant violations MUST be tested with:
    - `assertThatThrownBy(...)`
    - expected exception type (domain exception or IllegalArgumentException depending on modelling.definition)
    - optional message check only if message is part of contract

## 3.3 Equality & Identity
- Domain identity MUST be tested via explicit identity fields (e.g., `BookingId`), not object identity.
- Value Objects SHOULD be compared by value.


# 4. Test Data & Fixtures

## 4.1 Principles
- Tests MUST be readable and intention-revealing.
- Prefer domain builders or factory methods over raw constructors when setup is non-trivial.

## 4.2 Builder Rules
- Builders MUST live in test scope.
- Builders MUST create valid domain objects by default.
- If invalid objects are needed, they MUST be created intentionally and explicitly in the test.

## 4.3 Data Randomness
- Avoid randomness in tests unless it is deterministic (seeded) and justified.
- If randomness is used, it MUST be reproducible.


# 5. Naming & Structure

## 5.1 Test Names
Tests MUST be behavior-driven and domain-oriented.

Preferred styles:
- `should_<expected_behavior>_when_<condition>`
- AND JUnit5 `@DisplayName` with Given/When/Then narrative

## 5.2 Package Placement
Tests SHOULD mirror production packages to support navigation and traceability.

Examples:
- `...domain...` tests mirror domain packages
- `...application...` tests mirror use case packages
- `...adapters.persistence...` tests mirror persistence adapter packages
- `...adapters.inbound.rest...` tests mirror REST adapter packages


# 6. Coverage Expectations (Qualitative)

This project targets **meaningful coverage**, not numeric vanity.

Mandatory coverage themes:
- Every Aggregate invariant has at least one test.
- Every Use Case has:
    - at least one happy path test
    - at least one failure scenario test
- Every non-trivial query in persistence adapter has an integration test.

If coverage is intentionally missing, it MUST be documented in the Use Case Spec (or ADR if strategic).


# 7. Quality Gates (Merge Blockers)

A change MUST NOT be considered complete unless:

- `./gradlew clean test` succeeds
- `./gradlew build` succeeds
- All new/changed behavior is test-covered according to this definition
- No ignored/disabled tests are introduced
- No flaky tests are introduced


# 8. Anti-Patterns (Forbidden)

- Tests that assert implementation details (framework internals, private methods)
- Controller tests that validate business logic instead of use case behavior
- Integration tests without Flyway migrations
- Tests that depend on execution order
- Over-mocking domain behavior (mocking value objects, mocking aggregates)
- Snapshot-like tests without explicit behavioral intent
- “Green tests” that do not assert anything meaningful


# 9. Traceability Requirement

Every code change MUST be traceable to at least one spec.

Minimum traceability for tests:
- Domain Test ↔ Domain Spec / invariant reference
- Use Case Test ↔ Use Case Spec acceptance criteria
- Adapter Integration Test ↔ Port specification / adapter contract

If a test cannot be traced to a spec, the spec is missing or the test is noise.
