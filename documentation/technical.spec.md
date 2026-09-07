# Technical Specification

## Purpose

Defines the technical baseline and implementation constraints for a service built from this
template.

**This is a profile document.** It ranks fifth in `CLAUDE.md`'s authority order and
describes *this* project's technical choices. A service owns this file and may replace it —
that is the point of it being a profile. What it may **not** silently drop are the
constraints below marked as fixed by an ADR, because those are what the enforcement gates
check.

The stack is deliberately opinionated, in support of:
- Domain-centric design (DDD)
- Hexagonal Architecture (Ports & Adapters)
- Spec Driven Development (SDD)
- Always-Valid domain modelling


## Tech Stack

### Language & Runtime
- **Kotlin 2.3** on **JVM 17** — `adr/0010-jvm-17-baseline.adr.md`
- The Gradle toolchain in `gradle/libs.versions.toml` is the **single authoritative**
  version declaration. `.sdkmanrc`, the CI pin and IDE settings derive from it.
- Compiler settings are gates, not preferences: `allWarningsAsErrors = true` and
  `-Xjsr305=strict`. The second is what makes the null policy in
  `coding-style.definition.md` § 1.4 enforceable across a Java-interop boundary.
- Moving the baseline is an ADR trigger.

### Framework
- **Spring Boot 4.x** on Spring Framework 7
- Kotlin plugins: `jvm`, `plugin.spring`, `plugin.jpa`
- Note: Boot 4 moved its test-slice annotations into per-module artifacts. `@DataJpaTest`
  needs `spring-boot-data-jpa-test`; `@AutoConfigureTestDatabase` needs
  `spring-boot-jdbc-test`. The failure mode is an unresolved import, not a helpful message.

### Build Tool
- **Gradle 8.14** via the wrapper, Kotlin DSL, version catalog
- The configuration cache is **on**. Keep it that way: it was previously disabled by a
  plugin that no longer exists, and a plugin that breaks it should be questioned before it
  is accepted.

### API
- **REST and GraphQL** in parallel — `adr/0012-dual-delivery-transports.adr.md`
- GraphQL is schema-first: SDL in `src/main/resources/graphql/<context>/`
- Executable requests live in `api/` — see `file-naming.definition.md`

### Database & Migration
- **PostgreSQL** only — `adr/0013-postgresql-and-testcontainers.adr.md`
- **Flyway** for migrations
- No in-memory database, in any profile, for any purpose

### Persistence Access
- **The fixed rule:** no type in `core`, `shared.domain` or `shared.outport` may depend on a
  persistence API — `adr/0011-persistence-annotations-stay-out-of-the-domain.adr.md`
- **The ORM is a project choice.** This example uses Spring Data JPA, with `*JpaEntity`
  types in `outbound.persistence` and a mapper to the aggregate. A service may choose
  otherwise; the rule above does not move.
- Enforced by `DependencyRulesTest.rule6_noPersistenceTypeInTheCore` and
  `ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType`

### Testing
- JUnit 5, **AssertJ** for assertions (mandatory), mockito-kotlin for mocks
- **ArchUnit** for architecture enforcement — `adr/0007-archunit-boundary-enforcement.adr.md`
- **Testcontainers** for every `*IT`
- Two Gradle tasks: `test` (no Docker needed) and `integrationTest` (needs Docker). `check`
  depends on both

### Code Quality
- **ktlint via spotless** — style, configured in `.editorconfig`
- **detekt** — static analysis, configured in `config/detekt/detekt.yml`
- Every departure in either config carries its reason. A suppression without one is
  indistinguishable from a rule nobody understood — `adr/0014-quality-gates-are-executable.adr.md`


## Baseline Constraints

### Architectural Constraints
- The domain MUST remain framework-free: no Spring annotations, no persistence annotations,
  no JDBC or ORM types.
- All infrastructure dependencies live in adapters.
- Business rules live only in aggregates (preferred) or domain services (only for
  cross-aggregate or non-entity logic).
- The use-case layer coordinates ports; it does not implement domain rules.

### Dependency Constraints
- No new dependency without an ADR.
- No "convenience" dependency that leaks an abstraction boundary.
- No annotation processor or code-generation plugin for boilerplate: Kotlin's primary
  constructors and `data class` remove the need, and generated code is code no reviewer
  reads.


## Persistence Strategy

### Database
- PostgreSQL, one engine everywhere.
- Local development: `docker-compose/docker-compose.yaml`.
- Integration tests: their **own** Testcontainers instance, pinned to an explicit image tag.
  Never the developer's running stack — otherwise a test run depends on what happened to be
  up, and state leaks between runs.

### Migrations (Flyway)
- All schema changes MUST be Flyway migrations, in `src/main/resources/db/migration`.
- No manual schema changes. No `baseline-on-migrate` shortcut without an ADR.
- `spring.jpa.hibernate.ddl-auto` is `validate` and MUST stay that way. Flyway owns the
  schema; Hibernate verifies the mapping against it. That single setting is what makes every
  integration test also a check that the migrations and the entity mapping agree — the most
  common thing to break and the least likely to be caught by a unit test.
- **No implicit schema generation, ever.**

### Flyway Migration Classification

Naming: `V<version>__<TYPE>_<description>.sql`, where `<TYPE>` is one of:

| Type | Contents |
|------|----------|
| `DDL` | `CREATE`/`ALTER`/`DROP TABLE`, indexes, constraints |
| `DML` | `INSERT`/`UPDATE`/`DELETE`, seed data |
| `DCL` | `GRANT`, `REVOKE` |

Example: `V1__DDL_create_tour_booking.sql`, `V2__DML_seed_reference_data.sql`

Rules:
- DDL and DML MUST NOT be mixed in one migration.
- Seed data is an explicit DML migration.


## Testing Strategy

This document specifies only the **tooling**. Everything else lives where it belongs:

- taxonomy, assertion rules, coverage expectations, the canonical quality gates →
  `test.definition.md`
- when in the cycle a test is written (RED-first) → `tdd.definition.md`


## Build & Quality Gates

**Canonical list: `test.definition.md` § 7.** This document owns only the *commands*,
because commands are a property of the build tool:

```shell
./gradlew clean test          # fast: domain, use case, slice. No Docker.
./gradlew integrationTest     # every *IT, against PostgreSQL. Needs Docker.
./gradlew build               # includes check: spotlessCheck, detekt, both test tasks
```

Allowlisted in `.claude/settings.json` so agents can run them without prompting.


## Local Development Defaults

- Spring profiles: `local` for development, `test` for the test runtime.
- Start the database with `docker compose -f docker-compose/docker-compose.yaml up -d`.
- Logging MUST NOT leak sensitive data. SQL logging is for local debugging only, never a
  default.
- GraphiQL is enabled in development at `/graphiql`; it MUST be disabled in production.


## Non-Goals (Technical)

- No microservice split
- No reactive stack by default
- No "smart" framework inside the domain model
- No read side: no query ports, no projections, no CQRS. A limitation of the example, not a
  position — see `project.definition.md`
- No optimistic locking. The aggregates carry no `@Version`, so concurrent updates are
  last-write-wins. A service with contended aggregates must address this
