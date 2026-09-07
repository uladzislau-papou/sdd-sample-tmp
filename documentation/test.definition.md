# Test Definition (`test.definition.md`)

## Purpose

This document defines the testing strategy, rules, and quality gates.

Tests are not optional. They are the enforcement mechanism for:
- Always-Valid domain invariants
- Use case behavior
- Hexagonal architecture boundaries
- Persistence correctness (Flyway + the persistence adapter)


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

- **PostgreSQL** is the only database, in every profile
  (`adr/0013-postgresql-and-testcontainers.adr.md`).
- Integration tests start their **own** container through **Testcontainers**, pinned to an
  explicit image tag, from the shared `PostgresIntegrationTest` base class.
- **Flyway** MUST run migrations for any test that touches persistence. With
  `ddl-auto: validate`, that also makes every such test a check that the migrations and the
  entity mapping agree.
- The persistence adapter's own API — JPA here — is used to read and write state in adapter
  tests. Which ORM that is remains a project choice; what does not move is that no
  persistence type reaches the core (`adr/0011-...`).

### No in-memory database, for any purpose

The previous baseline used H2, on the grounds that it was fast and sufficient. It is neither
safe nor sufficient for a template real services start from: H2's dialect and PostgreSQL's
diverge, so a migration that applies on H2 can fail on PostgreSQL, and JPA behaves
differently over key generation, types and nulls in unique indexes. A suite that proves a
migration works on H2 has proved something about H2.

`technical.spec.md` used to carry a warning that Flyway's H2 support lagged and needed
version pinning. That warning was the symptom: the schema tooling and the test database were
drifting apart, and the code under test was not the code that would run.

### No test may create a file-based database

The rule is kept, generalised, because the defect it came from is about **profiles**, not
about H2.

A profile-specific `application-test.yml` loads only when the `test` profile is active. A
test that boots a Spring context without activating it falls back to `application.yml` — the
*production* datasource. Under H2 that silently wrote a database file into the project directory, which
survived across runs and which `./gradlew clean` did not remove, so run *n+1* inherited run
*n*'s state. That is order-dependence (§ 8) and latent flakiness (§ 7) arriving through
configuration rather than through test code.

Under PostgreSQL the same mistake fails differently — a connection refused, or worse, a
test writing to a developer's local database. Therefore:

- A test that boots a Spring context MUST scope it: a slice annotation, or an explicit
  profile.
- An integration test MUST take its connection from Testcontainers, never from
  `application.yml`.
- No test may write to a database the developer also uses.

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

For persistence (Flyway + the persistence adapter + a real PostgreSQL container):
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

Examples, in the actual ontology (`architecture.definition.md` § 3):
- `booking.core.domain.tourbooking` → `TourBookingTest`, `ParticipantCountTest`
- `booking.inbound.driver` → `ConfirmTourBookingDriverTest`
- `booking.inbound.listener` → `TourStartedListenerTest`
- `booking.inbound.rest` → `TourBookingRestControllerTest`
- `booking.inbound.graphql` → `TourBookingGraphQLControllerTest`
- `booking.outbound.persistence` → `TourBookingJpaRepositoryIT`
- `<root>.architecture` → the enforcement suite (ADR 0007, ADR 0014), which mirrors no
  production package because it is about the tree as a whole. Two of its tests read
  `documentation/` rather than the code: `ContextRegistryTest` and `SpecCitationsTest`
- `<root>.support` → shared test infrastructure, e.g. the Testcontainers base class. Not a
  bounded context, which is why the architecture tests import production classes only

> This section previously illustrated mirroring with `...application...`,
> `...adapters.persistence...` and `...adapters.inbound.rest...` — packages that do not
> exist and never have. It was the same dead vocabulary removed from
> `coding-style.definition.md` § 3.2, and `tasks.md` task 1.4.2 claimed to have purged it
> while missing this occurrence. Found by `ddd-hex-reviewer`.


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

1. `./gradlew clean test` succeeds — the fast suite: domain, use case and slice tests
2. `./gradlew integrationTest` succeeds — every `*IT`, against real infrastructure via
   Testcontainers. A separate task because it needs Docker, and a gate that cannot be
   run locally is a gate discovered in CI. `check` depends on both, so neither can be
   quietly skipped
3. `./gradlew build` succeeds
4. `./gradlew spotlessCheck` and `./gradlew detekt` succeed (both wired into `check`,
   so `build` covers them)
5. The ArchUnit suite in `src/test/.../architecture/` passes (ADR 0007). Note that
   `ContextRegistryTest` reads its registry from `architecture.definition.md` § 11, so
   this gate also fails when the document and the packages disagree
6. All new/changed behaviour is test-covered according to this definition
7. Every new behaviour was driven by a quoted RED failure (`tdd.definition.md` § 2),
   or is a behaviour-preserving change meeting § 2.1's evidence requirement
8. No ignored/disabled tests are introduced
9. No flaky tests are introduced
10. No test was weakened, loosened, or deleted to reach green
11. No architecture rule was weakened to reach green. Rules are enforcement; the
    definition they cite is authoritative, so a failing rule means the code is wrong
    unless the *definition* changed first (`file-usage.definition.md` § 5.1)
12. **Review returns `PASS` on the blocking axes**, and the reporting axes are addressed:

    | Axis | Owner | Authority |
    |------|-------|-----------|
    | architecture drift | `ddd-hex-reviewer` | **blocks** |
    | conformance to the spec (§ 7 criteria, § 10 boxes) | `conformance-reviewer` | **blocks** |
    | logical correctness | the review skill's logic axis | reported |
    | security | the review skill's security axis | reported |

    The split is deliberate, and it is what keeps this gate usable. The two blocking axes
    rest on executable rules and on list-matching, so they are near-deterministic. The two
    reporting axes are model judgement, where false positives are ordinary — and a
    blocking gate that cries wolf gets switched off entirely, taking the reliable axes with
    it. "Addressed" means each finding is either fixed or explicitly accepted with a
    reason; "zero security findings" is not an achievable merge condition and stating it as
    one would make the gate a lie.
13. Specs, port specs and the files in `api/` reflect the code as built

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
- API / Web Test ↔ Use Case Spec § 9 + the matching request in `api/uc<nn>-*.http` or
  `api/uc<nn>-*.graphql`

If a test cannot be traced to a spec, the spec is missing or the test is noise.

Traceability runs in both directions. A DoD item (`use-case spec` § 10) must
name the test that satisfies it — `covered by <TestClass>.<method>`, not
"implemented". An unnamed DoD item is untickable.
