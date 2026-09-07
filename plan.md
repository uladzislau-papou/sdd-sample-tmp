# Plan — Turn this repository into a reusable SDD template

**Read this first if you are picking the work up after time away.**

## What this repository is becoming

Today it is Alpine Booking: a Java 25 / jOOQ / REST reference implementation of
Spec-Driven Development, tactical DDD and Ports & Adapters, written by a single
author across 65 commits.

It is becoming **two things in one repository**:

1. A **template** for starting new Kotlin services that follow the same method.
2. A **working reference example** that proves the template compiles, its gates
   fire, and its documents describe something real.

The tour domain stays, deliberately. It is a *vehicle for the method*, not a
starting point for anyone's product. A foreign concrete domain is safer than an
abstract one: nobody copies `Booking` into a real service by inertia.

## What does not change

The process documents carry over untouched. They never mention a domain and
they are the actual value here:

`execution.playbook.md` · `loop.playbook.md` · `sdd.playbook.md` ·
`tdd.definition.md` · `domain-vs-use-case.definition.md` ·
`file-naming.definition.md`

## What becomes a project profile

Three documents describe *this* project rather than the method, and each new
service writes its own version:

`architecture.definition.md` · `coding-style.definition.md` · `technical.spec.md`

## Target stack

| | Value | Note |
|---|---|---|
| Language | Kotlin 2.3 | was Java 25 |
| Platform | JVM 17 | **downgrade** from Java 25 — deliberate, see Risks |
| Build | Gradle 8.14 | **downgrade** from 9.3.1 |
| Framework | Spring Boot 4 | unchanged |
| API | GraphQL **and** REST | REST only today |
| Persistence | Spring Data JPA, entities in the adapter | was jOOQ |
| Database | PostgreSQL (docker-compose) | was H2 |
| Integration tests | Testcontainers | was in-memory H2 |
| Architecture | Ports & Adapters, unchanged doctrine | |

The template fixes **one** persistence rule — *the domain holds no persistence
annotations* — and enforces it with ArchUnit. It does not fix the ORM: a banned
technology is a weak control, an executable rule is a strong one.

---

## Phases

### Phase 0 — Remove the external risk

`.github/workflows/publish-showcase.yml` force-pushes a rewritten history to a
public repository belonging to the original author. It has no place here under
any scenario. Delete first, before touching anything else.

### Phase 1 — Delete what does not survive

The example shrinks to three use cases across two bounded contexts:

| Use case | Context | Why it stays |
|---|---|---|
| UC01 `RequestTourBooking` | `booking` | aggregate creation; carries the dual-transport example |
| UC05 `StartTour` | `guide` | state transition; publishes `TourStarted` |
| UC06 `MarkBookingActive` | `booking` | event listener with **no** external API — the example of a DoD that closes by negating § 9 |

Everything else in `documentation/use-cases/`, `documentation/domain/` and
`documentation/ports/` goes, except the templates. `rest/` is renamed to `api/`.
The jOOQ code-generation chain leaves the build.

### Phase 2 — Move the platform

JVM 17, Gradle 8.14, Kotlin 2.3, Spring Data JPA, PostgreSQL, Testcontainers.
Dropping the Flyway Gradle plugin (it existed only for the jOOQ chain) allows
the Gradle configuration cache to be switched back on.

### Phase 3 — Rename the class roles

Spring for GraphQL annotates resolvers with `@Controller`, and
`coding-style.definition.md` already reserves `*Controller` for the REST adapter.
Two roles cannot share one name while `ClassRoleRulesTest` decides roles by name.

`*Controller` → `*RestController`, and the new role `*GraphQLController`
implementing `*GraphQLAPI`.

**This happens before the GraphQL adapter is written, not after.**

### Phase 4 — Port to Kotlin, tests first

Tests port before production code. That is not ceremony: a Kotlin test that
fails because the Kotlin production class does not exist yet *is* RED, arrived
at naturally. Porting production code first would mean days of unverifiable
code and every divergence discovered at the end.

One vertical slice at a time.

### Phase 5 — Remove the hardcoded identity

`ROOT` is a string literal in three ArchUnit tests, and `scanBasePackages`
repeats it. Both are derived instead — from the package the bootstrap class
actually lives in — and collapse into one shared test helper.

A `./gradlew initService` task does the one thing a Gradle property cannot:
move the package directories.

The registered-context list stops being copied. `architecture.definition.md`
§ 11 becomes a strict table, `ContextRegistryTest` reads it, and the two can no
longer disagree silently.

### Phase 6 — Generalize the documents

`README.md` says what the repository is. `project.definition.md` defines the
example, including honest non-goals. `project.definition.template.md` is the
blank a new service fills in.

The ADR set is rebuilt by one filter: a decision becomes a baseline ADR only if
it **does not contradict the hexagonal doctrine** and **holds for every** new
service. Baseline ADRs are marked `Accepted (inherited from template)`.

Out: ADR-0003 (the `guide` context is a domain decision), ADR-0008. Rewritten:
ADR-0006 (Java 25 → JVM 17 baseline). Not ADRs at all: MinIO/S3, Spring
StateMachine, JWT, the OTel stack, webhooks, the audit kernel, Playwright —
none of them hold for every service.

### Phase 7 — The two new skills

Last, deliberately. `/spec-create` fills § 9 in its new API shape and
`/code-review` checks conformance against the generalized template. Writing
them before Phase 6 means writing them against a format that changes the next
day.

Both are skills, not commands: neither fits in a thirty-line prompt file.

---

## Definition of Done for this plan

- [ ] `publish-showcase.yml` gone; no workflow can push outside this repository
- [ ] `./gradlew clean test build` green on JVM 17 / Gradle 8.14
- [ ] Zero `.java` files under `app/src`
- [ ] Three ArchUnit tests green, none containing a hardcoded package literal
- [ ] `ContextRegistryTest` derives its context list from `architecture.definition.md` § 11
- [ ] `./gradlew initService -PserviceName=… -PserviceGroup=…` produces a compiling, green service
- [ ] UC01 reachable over **both** REST and GraphQL, with `api/uc01-*.http` and `api/uc01-*.graphql`
- [ ] UC06 has no `api/` file and its spec says so explicitly
- [ ] No document outside `documentation/domain/`, `documentation/use-cases/` and the example's own specs names a tour, a booking or a guide
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree
- [ ] `spec-documenter` reports no gaps and no unresolved conflicts
- [ ] Four agents and six slash commands registered in `CLAUDE.md`, each list single-sourced
- [ ] `plan.md` and `tasks.md` replaced by empty `*.template.md`

---

## Risks

**The template has no consumer.** `risk-management-service` donates the stack
and receives nothing. Nothing else consumes the template yet, so the only
available verification is that the template builds itself and its own gates
fire. The first real service will find what this cannot.

**The platform downgrade has negative return.** JVM 17 and Gradle 8.14 are
older than what the repository already runs, and the change costs edits to
`.sdkmanrc`, `libs.versions.toml`, `build.gradle.kts`, the wrapper, the CI pin
and ADR-0006. It was chosen deliberately to match the donor service; recorded
here so nobody later mistakes it for an accident.

**The security axis ships unverified.** `/code-review` gains a security axis,
but the example has no authentication, no PII and no outbound calls. There is
nothing for that axis to find here. It first does real work in a real service.

**Provenance.** Every commit is authored by Dominik Galler and there is no
licence file. Scope was narrowed to personal reuse; this is not becoming a
company standard. The rewrite to Kotlin, the new ADR set and the generalized
documents leave little of the original text intact, but the origin is recorded
rather than forgotten.
