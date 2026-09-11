# Test Definition – JRL Contract Management (`test.definition.md`)

## Purpose

This document defines the testing strategy, rules, and quality gates for
JRL Contract Management.

Tests are not optional. They are the enforcement mechanism for:
- Always-Valid domain invariants
- Use case behavior
- Hexagonal architecture boundaries
- Persistence correctness (Flyway + JPA + PostgreSQL)


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
- **No mocking framework is on the classpath** (`technical.spec.md`, Testing).
  § 2.2 requires stubs over mocks; having no mocking library means the
  discouraged path is not available rather than merely discouraged.

## 1.2 Spring Boot
- Spring Boot test support MAY be used for:
    - adapter integration tests
    - GraphQL slice tests
    - wiring tests for application services

Domain tests MUST NOT require Spring.

## 1.3 Database for Tests

**PostgreSQL, never an in-memory substitute** (`technical.spec.md`, Persistence
Strategy). Flyway MUST run migrations for any test that touches persistence.

### Profile discipline

`application-test.properties` is **profile-specific**: it loads only when the
`test` profile is active. A test that boots a Spring context without activating it
falls back to `application.properties` — the *development* datasource — and writes
to whatever database a developer happens to have running locally.

That is worse here than it would be with a file-based H2, because the damage is not
a stray file in the working tree: it is rows written into, and truncated out of, a
database somebody was using. Order-dependence (§ 8) and data loss arriving through
configuration rather than through test code.

Therefore:

- Any test that starts a Spring context and genuinely needs a database MUST declare
  `@ActiveProfiles("test")`.
- A test that does **not** need a database MUST NOT start one — use a slice
  (§ 2.4), which auto-configures no `DataSource` at all.
- The test datasource MUST point at the `test_db` service (port 5433), never at the
  dev database on 5432. They are separate services in
  `docker-compose/docker-compose.yaml` for exactly this reason.

Because `bootstrap` sits outside every bounded-context package
(`architecture.definition.md` § 4.9), `@GraphQlTest` and `@SpringBootTest` cannot
find `ContractManagementApplication` by searching upwards. Each slice therefore
supplies its own minimal `@SpringBootApplication` in its own test package —
`MasterLeasingGraphQlTestApplication`, `IndividualLeasingGraphQlTestApplication`,
`MasterLeasingPersistenceTestApplication`,
`IndividualLeasingPersistenceTestApplication`. Follow that pattern rather than
widening a slice to the whole application.


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

**Money and dates get exact assertions.** `isEqualTo(Money.euro("152.5000"))`, not
`isCloseTo`. A tolerance on a monetary assertion hides the rounding defect the
`Money` type exists to prevent, and a leasing rate that is right to within a cent is
wrong (`modelling.definition.md`, Money). The same applies to derived dates: assert
the computed `termEnd`, not that it is "about three years out".

## 2.2 Use Case Tests (Mandatory)
**Scope:** Application layer (Drivers) with ports stubbed.
Always prefer good stubs before mocks — and no mocking framework is available (§ 1.1).

Rules:
- MUST be Spring-free. A driver is a constructor and a function; instantiating it
  directly is both faster and a better test of whether its dependencies are honest.
- MUST validate:
    - orchestration logic (which ports are called, and in what order where order matters)
    - emitted domain events
    - transactional expectations (conceptually, not by inspecting Spring internals)
    - failure scenarios (e.g., missing aggregate, invalid state)

**Order matters more here than in most domains and must be asserted where it does.**
UC05 validates the leasing terms it fetched *before* persisting; UC04's cancellation
validates *before* fanning out. A test that only asserts the end state passes
whichever order the driver used, and the wrong order is a partially applied change.

A stub that records its calls is the tool for this. Keep them in the test package
next to the tests that use them.

## 2.3 Adapter Integration Tests (Mandatory when adapter changes)
**Scope:** Persistence adapters and infrastructure adapters. Named `*IT`.

For persistence (Flyway + JPA + PostgreSQL):
- MUST run Flyway migrations.
- MUST verify roundtrip correctness: write → read → domain equivalence.
- MUST validate query semantics that matter for the domain:
    - uniqueness constraints
    - the selection criteria in § 4.6 write-side queries — a wrong column or literal
      in a query is invisible to a stub-based driver test
    - concurrency-relevant behavior where possible (at least idempotency)
- MUST assert **scale and rounding survive the round trip** for every monetary and
  percentage column. `numeric(19,4)` truncating a fifth decimal place is a silent
  data change, and the only place it can be caught is here.
- MUST assert that an aggregate returned by the repository is **detached**: mutating
  it without calling `update` must leave the database unchanged. This is the
  `architecture.definition.md` § 4.6 rule that JPA most readily breaks, and it cannot
  be checked structurally.

Rules:
- MUST NOT test internal framework behavior.
- MUST test system behavior at the adapter boundary.

### Skipping is permitted; skipping silently is not

Integration tests guard on `PostgresAvailability` and skip when the server is
unreachable, so a clone without Docker still has a green domain suite.

This is a real hole and is closed from the other side: CI provides Postgres as a
service container and `.github/workflows/build.yml` **fails if any `*IT` reported a
skip**. A persistence suite that skipped and a persistence suite that passed produce
the same green tick otherwise, and that is the single most dangerous shape a test
suite can have.

A test MUST NOT use any other skip condition. `@Disabled` is forbidden outright
(§ 7 item 7).

## 2.4 GraphQL Tests (As Needed)
**Scope:** Inbound adapters (`*GraphQLController`, `*ExceptionResolver`).

Rules:
- MUST exist for:
    - every operation in the schema
    - argument validation rules
    - **error classification mapping** — the GraphQL analogue of HTTP status mapping
    - the error response contract
- SHOULD use `@GraphQlTest` with `GraphQlTester` rather than the full application.
- MUST map each operation to a Use Case (traceability).

**The assertion is on `extensions.classification`, not on a status code.** Every
GraphQL response is `200 OK`; asserting that proves nothing. A test that a
not-found returns `NOT_FOUND` and an illegal transition returns `CONFLICT` is the
only thing standing between a client and two failures it cannot tell apart
(`architecture.definition.md` § 4.5).

**The schema itself is under test.** `@GraphQlTest` loads the schema files, so a
field removed from the schema or a resolver with no matching field fails here. That
is deliberate coverage, not a side effect: the schema is the contract
(§ 3.3 of `coding-style.definition.md`).


# 3. Assertion Rules (Strict)

## 3.1 AssertJ is Mandatory
- Do not use `assertEquals`, `assertTrue`, or Kotlin's `kotlin.test` assertions.
- Prefer AssertJ fluent style with meaningful failure messages.

## 3.2 Exception Expectations
- Invariant violations MUST be tested with:
    - `assertThatThrownBy(...)`
    - the expected exception type — the **specific** domain exception, never a
      supertype. `isInstanceOf(RuntimeException::class.java)` passes for every
      failure including the wrong one
    - a message check only if the message is part of the contract

## 3.3 Equality & Identity
- Domain identity MUST be tested via explicit identity fields (e.g.
  `MasterLeasingContractId`), not object identity.
- Value Objects SHOULD be compared by value. Kotlin `data class` equality makes this
  the default; do not assert on individual properties when the whole object is
  comparable.


# 4. Test Data & Fixtures

## 4.1 Principles
- Tests MUST be readable and intention-revealing.
- Prefer domain factory functions over raw constructors when setup is non-trivial.

## 4.2 Fixture Rules
- Fixtures MUST live in test scope.
- Fixtures MUST create valid domain objects by default.
- If invalid objects are needed, they MUST be created intentionally and explicitly in the test.
- Prefer Kotlin default arguments on a fixture function over a builder class. A
  fixture with one overridden argument reads as
  `aMasterLeasingContract(status = CANCELLED)`, which states what the test is about
  and hides what it is not.

## 4.3 Data Randomness
- Avoid randomness in tests unless it is deterministic (seeded) and justified.
- **Never `Instant.now()` or `LocalDate.now()` in a test.** A domain in which every
  invariant is date arithmetic will produce a suite that passes until a term boundary
  or a leap day. Fix the clock: a stub `ClockPort` returning a constant, and literal
  dates in fixtures.


# 5. Naming & Structure

## 5.1 Test Names
Tests MUST be behavior-driven and domain-oriented.

**Convention: `<method>_<condition>_<expectedResult>`.**

```
activate_throwsInvalidMasterLeasingContractStateException_whenAlreadyCancelled
issue_rejectsTermStart_beforeMasterContractActivationDate
terminate_byMasterContract_whenAlreadyTerminated_isIdempotentNoOp
update_persistsRatePerMonth_atScaleFour
```

The method under test comes first, so tests for one method sort together and a failure
name points straight at the production method. Where there is no single method — value
object construction, for instance — the subject takes its place
(`negativeAmountThrows`, `blankElvNumberThrows`).

detekt's `FunctionNaming` rule excludes `**/test/**` by default, which is what allows
this convention; that exclusion is inherited from the default config rather than
configured, and it must not be removed.

`@DisplayName` with a Given/When/Then narrative MAY be added where the name alone is
not self-explanatory. It is not required.

## 5.2 Package Placement
Tests MUST mirror production packages to support navigation and traceability.

Examples, in the actual ontology (`architecture.definition.md` § 3):
- `masterleasing.core.domain.masterleasingcontract` → `MasterLeasingContractTest`, `CreditLimitTest`
- `masterleasing.inbound.driver` → `ActivateMasterLeasingContractDriverTest`
- `masterleasing.inbound.graphql` → `MasterLeasingContractGraphQLControllerTest`
- `masterleasing.outbound.persistence.write` → `MasterLeasingContractPersistenceAdapterIT`
- `individualleasing.inbound.listener` → `MasterLeasingContractActivatedListenerTest`
- `com.jobradleasing.contractmanagement.architecture` → the ArchUnit suite (ADR 0007),
  which mirrors no production package because it is about the tree as a whole


# 6. Coverage Expectations (Qualitative)

This project targets **meaningful coverage**, not numeric vanity.

**JaCoCo produces a report; there is no threshold, and that is a decision rather
than an omission.** A percentage measures lines executed, which is not what this
document asks for — the mandatory themes below are about *which behaviours* are
covered, and a line-coverage gate can be satisfied without asserting anything (§ 8
forbids exactly that). The report exists because it is useful for spotting an
untouched package; making it a gate would replace judgement with a number that can
be gamed by a test that calls a method and asserts nothing.

The enforcement mechanism is instead:

- every DoD item names the test that satisfies it (§ 9), so coverage is traceable
  per criterion rather than aggregate;
- `ddd-hex-reviewer` reports production branches the diff adds without a test;
- where a batch of tests passes on first run, mutation testing proves they are not
  vacuous (`tdd.definition.md` § 2.2).

Mandatory coverage themes:
- Every Aggregate invariant has at least one test.
- Every Use Case has at least one happy path test and at least one failure scenario test.
- Every non-trivial query in a persistence adapter has an integration test.
- Every monetary or date calculation has a test asserting an **exact expected value**.
- Every GraphQL operation has a test asserting its success shape and each documented
  error classification.

If coverage is intentionally missing, it MUST be documented in the Use Case Spec (or ADR if strategic).


# 7. Quality Gates (Merge Blockers)

**Canonical list. This is the only quality-gate list in the project** — it
supersedes any copy in `sdd.playbook.md` or `technical.spec.md`, both of which
point here.

A change MUST NOT be considered complete unless:

1. `./gradlew clean test` succeeds
2. `./gradlew build` succeeds
3. `./gradlew spotlessCheck` succeeds (wired into `check`, so `build` covers it)
4. `./gradlew detekt` succeeds
5. The ArchUnit suite in `src/test/.../architecture/` passes (ADR 0007)
6. All new/changed behavior is test-covered according to this definition
7. No ignored/disabled tests are introduced, and no `*IT` skipped in CI (§ 2.3)
8. No flaky tests are introduced
9. No test was weakened, loosened, or deleted to reach green
10. No architecture rule was weakened to reach green. Rules are enforcement; the
    definition they cite is authoritative, so a failing rule means the code is wrong
    unless the *definition* changed first (`file-usage.definition.md` § 5.1)
11. Every new behaviour was driven by a quoted RED failure (`tdd.definition.md` § 2),
    or is a behaviour-preserving change meeting § 2.1's evidence requirement
12. `ddd-hex-reviewer` returns `PASS`
13. Specs, port specs and `graphql/*.graphql` reflect the code as built

Gates are merge blockers, not advisories — see `sdd.playbook.md` § 5 for the
principle. There is no partial credit.

The outer loop (`loop.playbook.md`) uses this list verbatim as one of its three
exit conditions, which is why it must exist in exactly one place.


# 8. Anti-Patterns (Forbidden)

- Tests that assert implementation details (framework internals, private functions)
- GraphQL tests that validate business logic instead of use case delegation and error mapping
- Integration tests without Flyway migrations
- Tests that depend on execution order
- Over-mocking domain behavior (stubbing value objects, stubbing aggregates)
- Snapshot-like tests without explicit behavioral intent
- “Green tests” that do not assert anything meaningful
- `isCloseTo` on a monetary assertion (§ 2.1)
- Asserting on a supertype of the expected exception (§ 3.2)
- A test that reads the current date or time (§ 4.3)


# 9. Traceability Requirement

Every code change MUST be traceable to at least one spec.

Minimum traceability for tests:
- Domain Test ↔ Domain Spec / invariant reference
- Use Case Test ↔ Use Case Spec `AC-NN` (cite the identifier, not the prose)
- Adapter Integration Test ↔ Port specification / adapter contract
- GraphQL Test ↔ Use Case Spec § 9 + the matching `graphql/uc<nn>-*.graphql` operation

If a test cannot be traced to a spec, the spec is missing or the test is noise.

Traceability runs in both directions. A DoD item (`use-case spec` § 10) must
name the test that satisfies it — `covered by <TestClass>.<method>`, not
"implemented". An unnamed DoD item is untickable.
