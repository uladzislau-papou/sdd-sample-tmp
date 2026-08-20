# Test Definition – Alpine Booking (`test.definition.md`)

## Purpose

This document defines the testing strategy, rules, and quality gates for Alpine Booking.

Tests are not optional. They are the enforcement mechanism for:
- Always-Valid domain invariants
- Use case behavior
- Hexagonal architecture boundaries
- Persistence correctness (Flyway + jOOQ)


# 0. Scope of This Document

This document defines **what** a test asserts: taxonomy, assertion rules,
fixtures, naming, coverage, and the canonical quality gates (§ 7).

It does **not** define *when* a test is written. That is `tdd.definition.md`,
which mandates test-first (RED before any production code) and owns the RED
evidence rule. The two documents are complementary and must not restate each
other.

- "Which assertion style, at which layer, covering what?" → this document
- "Written before or after the code, and how is that proven?" → `tdd.definition.md`


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

### No test may create a file-based database

`application-test.yml` (in-memory H2) is **profile-specific**: it loads only when the
`test` profile is active. A test that boots a Spring context without activating it
falls back to `application.yml` — the *production* datasource, `jdbc:h2:file:./data/…`
— and writes a real database file relative to the test JVM's working directory
(`app/`, for a Gradle `Test` task).

That file survives across runs, and `./gradlew clean` does not remove it, so run *n+1*
inherits run *n*'s state. This is order-dependence (§ 8) and latent flakiness (§ 7)
arriving through configuration rather than through test code.

Therefore:

- Any test that starts a Spring context and genuinely needs a database MUST declare
  `@ActiveProfiles("test")`.
- A test that does **not** need a database MUST NOT start one — use a slice
  (§ 2.4), which auto-configures no DataSource at all.
- `app/data/` must never appear after `./gradlew clean test`. If it does, a test is
  running against the production datasource.

Because `bootstrap` sits outside every bounded-context package
(`architecture.definition.md` § 4.9), `@WebMvcTest` and `@SpringBootTest` cannot find
`AlpineBookingApplication` by searching upwards. Each slice therefore supplies its own
minimal `@SpringBootApplication` in its own test package — `WebTestApplication`,
`GuideWebTestApplication`, `PersistenceTestApplication`, `GuidePersistenceTestApplication`.
Follow that pattern rather than widening a slice to the whole application.


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
Always prefer good stubs before mocks.

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

**Convention: `<method>_<condition>_<expectedResult>`.**

```
confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed
markActive_idempotent_whenAlreadyActive_noEventEmitted
start_usesClockPort_whenStartedAtIsEmpty
update_changesParticipantCount_inDatabase
```

The method under test comes first, so tests for one method sort together and a failure
name points straight at the production method. Where there is no single method — value
object construction, for instance — the subject takes its place
(`value0Throws`, `blankEmailThrows`).

`@DisplayName` with a Given/When/Then narrative MAY be added where the name alone is
not self-explanatory. It is not required, and none of the current tests use it.

> This section previously listed `should_<behavior>_when_<condition>` as the preferred
> style. **No test in the repository has ever used it** — all 145 use the
> method-first form above. The written rule was documenting an aspiration rather than
> the convention, so it has been replaced with the real one. If the aspiration is
> preferred, that is a rename of every test method and belongs in its own increment.

## 5.2 Package Placement
Tests SHOULD mirror production packages to support navigation and traceability.

Examples:
- `...domain...` tests mirror domain packages
- `...application...` tests mirror use case packages
- `...adapters.persistence...` tests mirror persistence adapter packages
- `...adapters.inbound.rest...` tests mirror REST adapter packages


# 6. Coverage Expectations (Qualitative)

This project targets **meaningful coverage**, not numeric vanity.

**No coverage tool is configured, and that is a decision rather than an omission.**
There is no jacoco, no threshold, no report. A percentage would measure lines executed,
which is not what this document asks for — the mandatory themes below are about *which
behaviours* are covered, and a line-coverage gate can be satisfied without asserting
anything (§ 8 forbids exactly that). The enforcement mechanism here is instead:

- every DoD item names the test that satisfies it (§ 9), so coverage is traceable
  per criterion rather than aggregate;
- `ddd-hex-reviewer` reports production branches the diff adds without a test;
- where a batch of tests passes on first run, mutation testing proves they are not
  vacuous (`tdd.definition.md` § 2.2).

Revisit if the project grows past the point where a human can hold the coverage map —
but add it as a *report*, not a gate, unless there is a specific behaviour a threshold
would have caught.

Mandatory coverage themes:
- Every Aggregate invariant has at least one test.
- Every Use Case has:
    - at least one happy path test
    - at least one failure scenario test
- Every non-trivial query in persistence adapter has an integration test.

If coverage is intentionally missing, it MUST be documented in the Use Case Spec (or ADR if strategic).


# 7. Quality Gates (Merge Blockers)

**Canonical list. This is the only quality-gate list in the project** — it
supersedes the copies that previously lived in `sdd.playbook.md` § 5 and
`technical.spec.md`. Both now point here.

A change MUST NOT be considered complete unless:

1. `./gradlew clean test` succeeds
2. `./gradlew build` succeeds
3. `./gradlew spotlessCheck` succeeds (wired into `check`, so `build` covers it)
4. The ArchUnit suite in `app/src/test/.../architecture/` passes (ADR 0007)
5. All new/changed behavior is test-covered according to this definition
6. Every new behaviour was driven by a quoted RED failure (`tdd.definition.md` § 2),
   or is a behaviour-preserving change meeting § 2.1's evidence requirement
7. No ignored/disabled tests are introduced
8. No flaky tests are introduced
9. No test was weakened, loosened, or deleted to reach green
10. No architecture rule was weakened to reach green. Rules are enforcement; the
    definition they cite is authoritative, so a failing rule means the code is wrong
    unless the *definition* changed first (`file-usage.definition.md` § 5.1)
11. `ddd-hex-reviewer` returns `PASS`
12. Specs, port specs and `rest/*.http` reflect the code as built

Gates are merge blockers, not advisories — see `sdd.playbook.md` § 5 for the
principle. There is no partial credit.

The outer loop (`loop.playbook.md`) uses this list verbatim as one of its three
exit conditions, which is why it must exist in exactly one place.


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
- Use Case Test ↔ Use Case Spec `AC-NN` (cite the identifier, not the prose)
- Adapter Integration Test ↔ Port specification / adapter contract
- API / Web Test ↔ Use Case Spec REST section + the matching `rest/uc<nn>-*.http` request

If a test cannot be traced to a spec, the spec is missing or the test is noise.

Traceability runs in both directions. A DoD item (`use-case spec` § 10) must
name the test that satisfies it — `covered by <TestClass>.<method>`, not
"implemented". An unnamed DoD item is untickable.
