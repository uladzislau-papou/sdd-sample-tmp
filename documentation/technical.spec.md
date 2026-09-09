# Technical Specification

## Purpose

Defines the technical baseline and implementation constraints for the Contract Management
service.

**This is a profile document.** It ranks fifth in `CLAUDE.md`'s authority order and
describes *this* project's technical choices. What it may **not** silently drop are the
constraints marked as fixed by an ADR, because those are what the enforcement gates check.

The stack comes from `risk-management-service`, the platform's existing Kotlin service. Its
*architecture* deliberately does not — that service is layered per context with `@Entity` in
its domain packages, which is the drift `adr/0011-persistence-annotations-stay-out-of-the-domain.adr.md`
exists to prevent. Where this document says "the platform's other service", it means the
stack donor and nothing more.

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
- **GraphQL only**, for now — `adr/0020-graphql-as-the-only-transport.adr.md`. The codebase
  still supports both transports over one core
  (`adr/0012-dual-delivery-transports.adr.md`); nothing here forbids REST, and REST arrives
  with the first machine consumer that needs it.
- `springdoc-openapi` is **not** a dependency. It describes REST, and there is no REST to
  describe. It is added in the same increment as the first REST endpoint.
- GraphQL is schema-first: SDL in `src/main/resources/graphql/<context>/`
- Executable requests live in `api/` — see `file-naming.definition.md`

### Database & Migration
- **PostgreSQL** only — `adr/0013-postgresql-and-testcontainers.adr.md`
- **Flyway** for migrations
- No in-memory database, in any profile, for any purpose

### Persistence Access
- **The fixed rule:** no type in `core`, `shared.domain` or `shared.outport` may depend on a
  persistence API — `adr/0011-persistence-annotations-stay-out-of-the-domain.adr.md`
- **The ORM for this service is Spring Data JPA**, with `*JpaEntity` types in
  `outbound.persistence` and a mapper to the aggregate. Same as the platform's other
  service, so the operational knowledge transfers; unlike it, the entities may not be the
  aggregates.
- There is one bounded context (`adr/0024-one-context-with-master-as-the-aggregate-root.adr.md`)
  and one database. A foreign key **inside** an aggregate is expected: `contract.master_id`
  references `master` and carries `ON DELETE CASCADE`, because a contract cannot outlive its
  Master. A foreign key **between** aggregates would be the thing to argue about, and there is
  no second aggregate to argue about it with.
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

Example: `V1__DDL_create_master.sql`, `V2__DML_seed_reference_data.sql`

Table names are prefixed with their context — `mlc_…`, `ilc_…` — for the same reason the
foreign-key rule above exists: the prefix makes the separability constraint visible in a
migration's diff.

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


## Outbound Integration

**There is none.** Nothing is synchronised anywhere, nothing is fetched from a foreign system,
and no object storage exists (`project.definition.md`, Non-Goals). Domain events are published
through `DomainEventPublisher` inside the caller's transaction (`adr/0002`) and logged; nothing
consumes them.

Two rules apply the moment that changes, and both are recorded now rather than rediscovered:

- Foreign representations stop at the adapter. No foreign type reaches `core` or
  `shared.domain`. `adr/0017-contract-data-ownership-boundary.adr.md` is withdrawn with the
  domain that motivated it and still carries the argument.
- A foreign call never happens inside our transaction. `adr/0019` and `adr/0022` are withdrawn
  with the same domain and record why an outbox, written synchronously in the caller's
  transaction, was the answer.

- No state-machine framework — `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`.


## Operational Baseline

Taken from the platform's other service, so that this one behaves the same way in
production:

- **Actuator** plus micrometer, exporting OTLP and Prometheus.
- **Authentication is not implemented.** Verifying a JWT is production code and arrives behind
  a failing test and a spec, which is why `adr/0021` deliberately stopped short of it. Every
  GraphQL operation is unauthenticated today; `notes.md` carries it as the next real increment.
- **lefthook** git hooks: `spotlessApply` and static analysis pre-commit, `test` pre-push,
  plus branch-name and commit-message validation. The pre-commit static-analysis hook runs
  `detektMain detektTest`, **not** `detekt` — the convenience task does not do
  type resolution, so a hook that ran it would go green while `check` goes red.
- **jacoco** produces a coverage report. It is **not** a merge gate: the canonical gate list
  is `test.definition.md` § 7 and a coverage threshold is deliberately absent from it. A number
  that blocks a merge gets defended rather than acted on, and the weakest areas here — an
  invented domain's rules, and sixteen architecture rules currently matching nothing
  (`adr/0026`) — are not things coverage measures.


## Non-Goals (Technical)

- No microservice split before the domain is understood —
  `adr/0016-single-deployable-for-the-mvp.adr.md`
- No reactive stack by default
- No "smart" framework inside the domain model
- No read side yet: no query ports, no projections, no CQRS. The MVP's display requirements
  need one, and the first display use case writes the ADR that chooses the pattern — see
  `project.definition.md` and `notes.md`
- No object storage, no webhook delivery, no audit trail as a feature. Each exists in the
  platform's other service and is a reference to read, not a dependency to add
- No optimistic locking. The aggregates carry no `@Version`, so concurrent updates are
  last-write-wins. A service with contended aggregates must address this
