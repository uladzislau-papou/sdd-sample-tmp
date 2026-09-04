# Test Definition – Risk Management Service (`test.definition.md`)

## Purpose

The testing strategy, rules, and quality gates for RMS.

Tests are the enforcement mechanism for:

- Transition legality in the KYC case lifecycle and the QES session lifecycle
- Provider translation correctness — the place a silent mapping bug costs the most
- Webhook idempotency and outbox delivery semantics
- Authorization: scope enforcement on every reachable surface
- The GraphQL and REST contracts

---

# 0. Scope of This Document

This document defines **what** a test asserts: taxonomy, assertion rules, fixtures,
naming, coverage, and the canonical quality gates (§ 7).

It does **not** define *when* a test is written. That is `tdd.definition.md`.

- "Which assertion style, at which layer, covering what?" → this document
- "Written before or after the code, and how is that proven?" → `tdd.definition.md`

---

# 1. Tooling Baseline

## 1.1 Frameworks

- **JUnit 5** (`useJUnitPlatform`) is the runner.
- **AssertJ** is the preferred assertion library for new tests.
- **mockito-kotlin** is the collaborator-doubling tool (`whenever`, `verify`, `mock`,
  `argumentCaptor`).
- **`spring-boot-starter-graphql-test`** — `GraphQlTester` / `ExecutionGraphQlServiceTester`
  for GraphQL controllers.
- **`json-schema-validator`** — validates emitted CloudEvents against
  `src/test/resources/audit/cloudevents.schema.json`.

### On assertion-library consistency

The existing suite is mixed: ~314 `org.junit.jupiter.api.Assertions` usages, ~111 AssertJ,
~24 `kotlin.test`. That is a fact, not a licence.

**New and modified tests use AssertJ.** Do not bulk-convert existing tests as a side
effect of an increment — a conversion sweep is its own increment with its own review.
Do not add new `Assertions.assertEquals` or `kotlin.test` usages.

## 1.2 Spring

Spring test support is used for controller and integration tests. **Pure service and
mapper tests must not start a Spring context** — construct the class with mocked
collaborators. A `@SpringBootTest` where a plain constructor call would do costs the whole
suite seconds each time.

## 1.3 Database for tests

RMS runs on **PostgreSQL and only PostgreSQL** (`technical.spec.md` § 3.1). There is no
H2 substitute and no Testcontainers dependency.

Therefore:

- A test that genuinely needs the database guards on availability and skips when it is
  absent — `onb/integration/PostgresAvailability.kt` is the pattern. Follow it.
- A skipped-because-unavailable integration test is **not** coverage. Do not close a DoD
  item with a test that did not run in the environment where you claim it passed.
- Everything that can be tested without a database MUST be tested without one. Repository
  query semantics and Flyway migration correctness are the legitimate exceptions.

## 1.4 Playwright

`playwright/api/` holds API-level tests run against a **running** service
(`npm run smoke`, `npm run test:api`). They are not part of `./gradlew test` and are not a
substitute for it. Use them for end-to-end contract confidence, not for business-rule
coverage.

---

# 2. Test Taxonomy

## 2.1 Service tests (mandatory)

**Scope:** a single `@Service` with its collaborators mocked.

This is the core of the suite — it is where business behaviour lives
(`modelling.definition.md` § 1).

Rules:

- No Spring context.
- MUST cover the happy path **and** every rejection path the service can produce.
- MUST assert the **thrown type and the condition**, not just that something threw.
- MUST verify the side effects that matter: what was persisted, which audit event was
  emitted, which outbox row was enqueued — and, with `never()`, what was *not* done on a
  rejection path.
- Transition legality gets its own test per illegal source state that the contract names.

## 2.2 Mapper and translation tests (mandatory when a mapping changes)

**Scope:** `api/mapper/*`, provider model ↔ our model.

This is the highest-value, lowest-cost test class in RMS. A provider adds a status, an
`else` branch swallows it, and a case silently stalls.

Rules:

- MUST cover **every** enum value in the source vocabulary. A mapping test that covers
  three of nine provider statuses is not a mapping test.
- MUST assert that an unknown input fails loudly rather than defaulting.
- Round-trip where a round-trip exists.

## 2.3 Controller tests (mandatory for every exposed operation)

**Scope:** GraphQL controllers via `GraphQlTester`; REST controllers via MockMvc slice.

Rules:

- MUST exist for every externally reachable operation.
- MUST cover: happy path, input-validation failure, and each documented error
  classification.
- MUST assert the **error classification** (`BAD_USER_INPUT`, `NOT_FOUND`, HTTP status),
  not merely that an error occurred.
- MUST NOT assert business rules — those belong in the service test. A controller test
  that re-tests the state machine is testing the wrong thing at the wrong cost.
- Every operation maps to a use case spec (§ 9).

## 2.4 Authorization tests (mandatory for every new surface)

**Scope:** the scope-enforcing filters and interceptors.

Every new externally reachable endpoint MUST have tests for:

- missing token → 401
- valid token, wrong scope → 403
- valid token, correct scope → passes through

`OnbScopeEnforcingFilterTest` and `WebhookJwtAuthFilterTest` are the references. An
endpoint shipped without these is a security regression, not a coverage gap.

## 2.5 Integration tests (mandatory when persistence or the schema changes)

**Scope:** repositories, Flyway migrations, outbox round-trips, GraphQL against a real
context. Named `*IntegrationTest`.

Rules:

- MUST run against real PostgreSQL with Flyway applied.
- MUST verify what a mock cannot: query semantics, constraint enforcement, optimistic-lock
  conflict behaviour, JSON column round-trips, migration effects.
- MUST guard on database availability (§ 1.3) and MUST NOT silently pass when skipped.

## 2.6 Audit tests (mandatory when an audit event changes)

Emitted CloudEvents are validated against the JSON schema in `src/test/resources/audit/`.
A new or changed event MUST have a test asserting its type, subject, attribution and
payload keys, and the catalogue in `docs/` MUST be updated in the same increment.

---

# 3. Assertion Rules

## 3.1 AssertJ for new tests

```kotlin
assertThat(result.status).isEqualTo(KycCaseStatus.ACCEPTED)
assertThatThrownBy { service.acceptCase(input) }
    .isInstanceOf(BadUserInputException::class.java)
    .hasMessageContaining("in status DECLINED")
```

## 3.2 Exception expectations

- Assert the **specific** type from `domain/exception`. `isInstanceOf(Exception::class.java)`
  asserts nothing.
- Assert on the message only where the message is part of the contract — for
  `BadUserInputException` on an illegal transition, it is: the caller reads it.
- **Never widen an expected exception type to make a test pass.** That is weakening
  (`tdd.definition.md` § 5).

## 3.3 Verifying collaborators

- `verify(repo).save(captor.capture())` and assert on the captured entity — not on the
  repository call alone. A `verify(...).save(any())` proves a call happened, not that the
  right thing was saved.
- Use `never()` on rejection paths. "Nothing was persisted and no audit event fired" is
  half the contract of a rejection.
- Do not mock entities or enums. Build real ones.

## 3.4 Time

Where a service reads the clock, inject it. A test asserting on `Instant.now()` with a
tolerance window is a flaky test waiting for a slow CI runner.

---

# 4. Test Data & Fixtures

- Shared fixtures live in `<module>/testsupport/` — `KycTestFixtures`,
  `OnbTestFixtures`, `qes/testfixtures/`. Extend those rather than duplicating builders.
- A fixture builds a **valid** object by default; invalid variants are constructed
  explicitly in the test that needs them, so the invalidity is visible at the assertion
  site.
- No randomness unless seeded and justified.
- No shared mutable state between tests. No reliance on execution order.
- Realistic-looking personal data in fixtures MUST be obviously synthetic.

---

# 5. Naming & Structure

## 5.1 Test names

`<methodOrSubject>_<condition>_<expectedResult>`:

```
acceptCase_throwsBadUserInput_whenCaseIsDeclined
collectParties_advancesToPartiesCollected_whenFunctionaryAndUboExist
mapProviderStatus_coversEveryIdnowStatus
enforceScope_returns403_whenScopeMissing
```

`@Nested` classes group by method or scenario where a class has many cases.

## 5.2 Package placement

Tests mirror production packages. `src/test/kotlin/.../kyc/service/KycCaseDecisionServiceTest.kt`
tests `src/main/kotlin/.../kyc/service/KycCaseDecisionService.kt`.

Suffixes: `*Test` for unit and slice; `*IntegrationTest` for anything requiring a real
database or a full context.

---

# 6. Coverage Expectations

JaCoCo runs and produces a report (`build/reports/jacoco/`). **There is no coverage
threshold and adding one is a deliberate decision, not a default.** A percentage measures
lines executed, which is orthogonal to the themes below.

Mandatory coverage themes — these are what a reviewer checks, not a number:

- Every service rejection path has a test.
- Every enum-to-enum mapping is covered for every source value.
- Every externally reachable operation has a controller test and an authorization test.
- Every Flyway migration that changes existing data has an integration test proving the
  effect.
- Every audit event has a schema-validated test.
- Every documented error classification has a test producing it.

If coverage is intentionally missing, it MUST be recorded in the use case spec (or an ADR
if strategic).

---

# 7. Quality Gates (Merge Blockers)

**Canonical list. This is the only quality-gate list in the project.**
`technical.spec.md` § 7 owns the commands; `sdd.playbook.md` § 5 owns the principle.

A change MUST NOT be considered complete unless:

1. `./gradlew spotlessCheck` passes (run `spotlessApply` first)
2. `./gradlew detekt` passes with no new findings
3. `./gradlew test` passes
4. `./gradlew build` succeeds — this includes compilation under `allWarningsAsErrors`
5. All new/changed behaviour is test-covered according to this definition
6. Every new behaviour was driven by a quoted RED failure (`tdd.definition.md` § 2), or is
   a behaviour-preserving change meeting § 2.1's evidence requirement
7. No `@Disabled` / `@Ignore` introduced
8. No flaky test introduced
9. No test weakened, loosened, or deleted to reach green
10. No `@Suppress` added to silence a compiler warning or a detekt finding without a
    stated, reviewed reason
11. `rms-architecture-reviewer` returns `PASS`
12. Specs, integration specs and `rest/*.http` reflect the code as built
13. New or changed configuration is in `.env.example`
14. New or changed audit events are in the `docs/` catalogue
15. No new or edited **applied** Flyway migration; new schema is a new timestamped file

Gates are merge blockers, not advisories. There is no partial credit.

The outer loop (`loop.playbook.md`) uses this list verbatim as one of its three exit
conditions, which is why it must exist in exactly one place.

### Not gates

Deliberately excluded, so nobody adds them by assumption:

- **JaCoCo coverage percentage** — a report, not a threshold (§ 6).
- **Playwright** (`npm run smoke`, `npm run test:api`) — requires a running service, so it
  cannot gate a code review. Run it before a deploy.

---

# 8. Anti-Patterns (Forbidden)

- Asserting that an exception was thrown without asserting its type
- `verify(repo).save(any())` as the only assertion about what was persisted
- A mapping test covering some of an enum's values
- A controller test re-testing business rules
- Starting a Spring context for a class that has no Spring dependency
- An integration test that silently passes when the database is unavailable
- Mocking entities, enums, or value types
- Tests that depend on execution order or shared mutable state
- Asserting on wall-clock time with a tolerance window
- A new endpoint with no authorization test
- Green tests that assert nothing meaningful

---

# 9. Traceability

Every code change MUST be traceable to at least one spec.

| Test kind | Traces to |
|-----------|-----------|
| Service test | Use case spec `AC-NN` (cite the identifier, not the prose) |
| Mapper test | The integration spec's translation table |
| Controller test | Use case spec § 9 API Contract + the matching `rest/uc<nn>-*.http` request |
| Authorization test | The scope declared in the use case spec § 9 |
| Integration test | The migration or the repository contract in the integration spec |
| Audit test | The event catalogue entry in `docs/` |

If a test cannot be traced to a spec, the spec is missing or the test is noise.

Traceability runs both ways: a DoD item (use case spec § 10) must name the test that
satisfies it — `covered by <TestClass>.<method>`, not "implemented". An unnamed DoD item
is untickable.
