# Technical Specification

## Purpose

This document defines the technical baseline and implementation constraints for
JRL Contract Management.

This is a reference-grade backend system. The stack is intentionally opinionated to
support:
- Domain-centric design (DDD)
- Hexagonal Architecture (Ports & Adapters)
- Spec Driven Development (SDD)
- Always-Valid domain modeling


## Tech Stack

### Language & Runtime
- **Kotlin 2.3** on **JVM 17** — see `adr/0006-jvm-and-kotlin-baseline.adr.md`.
  The Gradle toolchain in `build.gradle.kts` is the single authoritative version
  declaration; `.sdkmanrc`, the CI workflow and IDE settings are derived from it.
  The baseline tracks LTS releases only; moving it is an ADR trigger.
- Compiler flags, all three load-bearing:
  - `allWarningsAsErrors = true` — a deprecation is a build failure, not a line in
    a log nobody reads.
  - `-Xjsr305=strict` — JSR-305 nullability annotations on Java libraries are
    treated as hard types rather than platform types, which is what makes
    `coding-style.definition.md` § 1.4 enforceable at the Spring boundary instead
    of merely aspirational.
  - `jvmTarget = 17` — matched to the toolchain. A mismatch produces class files
    the runtime rejects at load time rather than at build time.

### Framework
- Spring Boot 4.x, on Spring Framework 7
- Spring GraphQL for the inbound adapter
- Spring Data JPA for persistence
- Gradle 9 with the Kotlin DSL, via the wrapper

The Kotlin Gradle plugins `plugin.spring` and `plugin.jpa` are applied. Both exist to
open final classes for framework proxying, and both are scoped by annotation —
`@Component`/`@Configuration` and `@Entity`/`@Embeddable` respectively. Neither can
reach `core.domain`, because nothing there carries such an annotation, and that is
precisely the guarantee `architecture.definition.md` § 4.1 rests on.

### Build Tool
- Gradle 9 via the Gradle Wrapper. Do not invoke a locally installed `gradle`.
- Versions are declared inline in `build.gradle.kts`, not in a version catalog.
  One module, one build file: a catalog indirects every coordinate through a second
  file for a project that has no second module to share it with.

### Database & Migration
- **PostgreSQL 17** in every environment, including tests
- **Flyway** for schema migrations
- No in-memory substitute. See *Persistence Strategy* below for why.

### Data Access
- **Spring Data JPA / Hibernate**
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never generates or updates
  schema; it verifies that the schema Flyway produced matches the mappings, and
  fails startup when it does not. `update` and `create-drop` are forbidden: they
  make the schema a side effect of the code, which is the coupling
  `architecture.definition.md` § 4.6 exists to prevent.
- `spring.jpa.open-in-view=false` — with it on, a lazily-initialised association
  resolves during view rendering, which means a query fires outside the transaction
  that was supposed to contain it and the transaction boundary in the driver is a
  fiction. It is on by default in Spring Boot; turning it off is deliberate.

### Testing
- JUnit 5 (via Spring Boot test support)
- **AssertJ** as the assertion library (mandatory for assertions)
- Spring GraphQL Test (`GraphQlTester`) for the inbound adapter
- **ArchUnit** for architecture enforcement (ADR 0007)
- No mocking framework is on the classpath, and that is a decision.
  `test.definition.md` § 2.2 requires stubs over mocks; adding a mocking library
  would make the easy path the one the definition discourages. A hand-written stub
  implementing a three-method port is a few lines and reads better in a failure.

**ArchUnit MUST be a version whose bundled ASM can read the current bytecode.** When
it cannot, it imports **zero** classes — silently, with no error or warning — and
every architecture rule then passes vacuously. ArchUnit's own "failed to check any
classes" guard is what makes this visible, and
`ContextRegistryTest.importer_findsProductionClasses` asserts the import size
directly rather than relying on it. Any future toolchain bump must re-verify that
ArchUnit still reads the new bytecode; a green architecture suite is not evidence
that it does.

### Static analysis and formatting
- **Spotless** with **ktlint** — formatting is applied, not merely checked. Unlike
  the Java reference this framework came from, there is no argument for
  hygiene-only: the codebase is new, so there is no pre-existing style to preserve
  and nothing for a reformat to bury.
- **detekt**, configured in `config/detekt/detekt.yml`. Every deviation from the
  default config carries a reason in that file. A rule relaxed without one is a
  disabled rule with extra steps (`coding-style.definition.md` § 9).
- **JaCoCo** produces a coverage *report*. It is deliberately not a gate — see
  `test.definition.md` § 6.


## Baseline Constraints

### Architectural Constraints
- The domain layer MUST remain framework-free: no Spring annotation, **no
  `jakarta.persistence` annotation**, no Jackson, no GraphQL type, no
  `org.springframework.data` type.
- All infrastructure dependencies live in adapters.
- Business rules live only in:
  - Aggregates (preferred)
  - Domain Services (only when cross-aggregate or non-entity logic is required)
- The application layer coordinates use cases and ports; it does not implement domain rules.

### Dependency Constraints
- No new dependency may be introduced without an ADR (`sdd.playbook.md` § 6 item 2).
- Avoid “convenience” dependencies that leak abstraction boundaries.


## Persistence Strategy

### Database

**PostgreSQL everywhere, including the test suite. There is no H2.**

This is a deliberate reversal of the "in-memory database for tests" convention, and
the reason is specific to this stack rather than general principle. With JPA, the
database dialect is not an implementation detail the test is insulated from — it
decides how an enum is stored, how `numeric(19,4)` rounds, how a `timestamptz`
round-trips a zone, what an optimistic-lock conflict throws, and what happens when
a `varchar(400)` receives 401 characters. A suite that passes against H2 and a
production system running Postgres are two different systems, and the difference
surfaces in exactly the monetary and temporal fields this domain is made of.

The cost is that the persistence suite needs a running server. That cost is bounded
by two mechanisms:

- `docker-compose/docker-compose.yaml` provides a `test_db` service on port 5433,
  separate from the dev database and backed by `tmpfs`. `make devup` starts it.
- Integration tests guard on `PostgresAvailability` and **skip themselves** when the
  server is unreachable, so a fresh clone with no Docker still gets a green domain
  and use-case suite (`test.definition.md` § 2.3).

The skip is the weak point and is treated as one: CI always provides Postgres as a
service container, and `.github/workflows/build.yml` **fails the build if an
integration test skipped anyway**. A silently skipped persistence suite is
indistinguishable from a passing one, which is the failure mode this arrangement
would otherwise introduce.

### Migrations (Flyway)
- All schema changes MUST be expressed as Flyway migrations.
- Location: `src/main/resources/db/migration`
- Rules:
  - No manual schema changes.
  - No ORM-generated DDL (`ddl-auto=validate`, above).
  - No `baseline-on-migrate` shortcuts unless explicitly justified in an ADR.

### Flyway Migration Classification

To improve clarity and auditability, SQL migrations are categorized by intent.

Naming convention:

```
V<version>__<type>_<description>.sql
```

Where `<type>` is one of:

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

```
V1__DDL_create_master_leasing_contract.sql
V2__DML_seed_reference_data.sql
```

Rules:

- DDL and DML MUST NOT be mixed in one migration.
- Seed data must be an explicit DML migration.
- No implicit schema generation via ORM.

### Column types for this domain

Three choices are stated here because getting them wrong is both easy and
expensive, and because `ddl-auto=validate` turns a mismatch into a startup failure
rather than a silent coercion:

| Concept | Column | Why not the obvious alternative |
|---------|--------|--------------------------------|
| Monetary amount | `numeric(19, 4)` | `double precision` cannot represent `0.01`, and the error compounds across a 36-month term (`modelling.definition.md`, Money) |
| Percentage / factor | `numeric(9, 6)` | A leasing factor is a rate to six places; `numeric(5,2)` silently rounds `0.017500` to `0.02` and changes every rate derived from it |
| Point in time | `timestamptz` | `timestamp` without a zone stores whatever the session offset happened to be, so the same instant reads differently from two JVMs |
| Business date | `date` | A `term_start` is a calendar date, not an instant. Storing it as `timestamptz` invites a timezone to shift it across a day boundary |

The last row is the distinction `architecture.definition.md` § 8.1 draws between an
observation and an agreed value, appearing again in the schema.


## Testing Strategy

This document specifies only the **tooling** side of testing (see `### Testing`
above: JUnit 5, AssertJ, GraphQlTester, Postgres, Flyway, ArchUnit).

- Test taxonomy, assertion rules and coverage expectations →
  `test.definition.md`
- When in the cycle a test is written (RED-first) → `tdd.definition.md`


## Build & Quality Gates

**Canonical list: `test.definition.md` § 7.**

This document owns only the *commands* those gates invoke, because the commands
are a property of the build tool:

```shell
./gradlew clean test
./gradlew build
```

`build` runs `check`, which covers `spotlessCheck` and `detekt`. Both are
allowlisted in `.claude/settings.json` so agents can run them without prompting.

`make gates` is shorthand for the pair.


## Local Development Defaults

Configuration
- Spring profiles:
  - default for local development, reading `.env` via `spring.config.import`
  - `test` for the test runtime (`src/test/resources/application-test.properties`)
- `make devsetup && make devup` gives a working Postgres pair with no further setup.

Observability / Logging (baseline)
- Logging must not leak sensitive data. In this domain that specifically includes
  salary-sacrifice amounts and employee identifiers: a `conversion_rate_per_month`
  in a log line is a disclosure of what someone earns net.
- SQL logging can be enabled only for local debugging (not default).


## Non-Goals (Technical)
- No microservice split
- No reactive stack
- No “smart” frameworks inside the domain model
- No REST adapter — GraphQL is the only inbound transport
  (`adr/0008-graphql-only-inbound-adapter.adr.md`)
- No in-memory database substitute for tests
