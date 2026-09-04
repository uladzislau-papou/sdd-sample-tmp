# Technical Specification

## Purpose

The technical baseline and implementation constraints for Risk Management Service.

Where this document and the service `README.md` describe the same thing, the README is
the operational reference (env vars, local setup, token generation). This document states
the **constraints** and what changing them requires.

---

## 1. Tech Stack

### 1.1 Language & Runtime

| | Version | Declared in |
|-|---------|-------------|
| Kotlin | 2.3.0 | `build.gradle.kts` |
| JVM toolchain / target | **17** | `build.gradle.kts` (`JavaLanguageVersion.of(17)`, `JvmTarget.JVM_17`) |
| Compiler flags | `-Xjsr305=strict`, `allWarningsAsErrors = true` | `build.gradle.kts` |

Kotlin plugins: `jvm`, `plugin.spring` (all-open, for proxying), `plugin.jpa` (no-arg
constructors for entities).

`build.gradle.kts` is the **single authoritative declaration**. Raising any of these is an
ADR trigger (`sdd.playbook.md` § 6 item 13). See `adr/0001-technical-stack.adr.md`.

### 1.2 Framework

- Spring Boot **4.0.3** (Spring Framework 7), `io.spring.dependency-management` 1.1.7
- `spring-boot-starter-web` (servlet, not reactive)
- `spring-boot-starter-graphql` — the primary API surface
- `spring-boot-starter-restclient` — outbound provider calls
- `spring-boot-starter-data-jpa` (Hibernate)
- `spring-boot-starter-validation` (Bean Validation)
- `spring-boot-starter-flyway` + `flyway-database-postgresql`
- `spring-boot-starter-actuator`
- `spring-statemachine-core` 4.0.1 — the KYC case lifecycle
- `spring-aop` / `spring-aspects` — QES metrics AOP
- `springdoc-openapi-starter-webmvc-ui` 3.0.2 — Swagger UI, **local/dev profiles only**

### 1.3 Supporting libraries

| Library | Purpose |
|---------|---------|
| `tools.jackson.module:jackson-module-kotlin` 3.0.0 | Kotlin data class (de)serialization |
| `io.github.oshai:kotlin-logging-jvm` 6.0.0 | Logging facade |
| `com.nimbusds:nimbus-jose-jwt` 10.8 | JWT verification and decoding |
| `org.bouncycastle:bcpkix/bcprov-jdk18on` 1.84 | Crypto for signature flows |
| `io.minio:minio` 9.0.0 | S3-compatible storage (local/dev) |
| `software.amazon.awssdk:s3` 2.31.70 | S3 storage (stage/prod) |
| `io.micrometer:micrometer-registry-otlp` / `-prometheus` | Metrics export |
| `me.paulschwarz:springboot3-dotenv` 5.1.0 | `.env` loading |

**No new dependency without an ADR** (`sdd.playbook.md` § 6 item 2).

### 1.4 Build

Gradle with the wrapper. `org.gradle.caching=true`, `org.gradle.parallel=true`.
Single-module build — `settings.gradle.kts` declares `risk-management-service` and no
subprojects. There is no `:app:` prefix on any task.

---

## 2. API Surface

### 2.1 GraphQL (primary)

| Endpoint | Scopes | Schema |
|----------|--------|--------|
| `POST /riskmanagement/qes/v1/graphql` | `api.qes.read` (query) / `api.qes.write` (mutation) | `graphql/identification/`, `graphql/signature/` |
| `POST /riskmanagement/kyc/v1/graphql` | `api.kyc.read` / `api.kyc.write` | `graphql/kyc/` |

Schemas live in `src/main/resources/graphql/`, composed onto the `root.graphqls`
`Query`/`Mutation` types via `extend type`. **The schema is part of the contract**: a
controller method with no schema field, or a schema field with no controller method, is a
broken contract.

### 2.2 REST

| Path | Auth |
|------|------|
| `/riskmanagement/onb/kyc/v1/*` | `api.onb.read` (GET/HEAD) / `api.onb.write` (POST/PUT/PATCH/DELETE) |
| `/webhooks/idnow`, `/webhooks/postident`, `/webhooks/signius` | Provider-specific; intentionally not JWT-gated |
| `/webhooks/radar/decisions` | JWT with scope `credit.radar.webhook` |
| `/actuator/**` | Open |

### 2.3 Authorization

Static HS256 JWT bearer tokens; the HMAC key is SHA-256 of `AUTH_JWT_SECRET`. `sub` must
match `auth.jwt.expected-subject`. No `exp`/`nbf`/`iat` validation — tokens are static.
`HS256` is enforced explicitly; `HS384`/`HS512`/`none` are rejected.

Operator identity is separate: decoded (not verified) from the platform `access_token`
cookie. See `architecture.definition.md` § 8.2 and
`adr/0006-jwt-scope-authorization.adr.md`.

---

## 3. Persistence

### 3.1 Database

**PostgreSQL.** `runtimeOnly("org.postgresql:postgresql")`. Local development runs it via
`docker-compose/`.

There is no H2 and no in-memory fallback. A test that needs a database needs a real
PostgreSQL — see § 6 and `test.definition.md` § 1.3.

### 3.2 ORM

Hibernate via `spring-boot-starter-data-jpa`. Entities are the domain model
(`adr/0003-jpa-entities-as-domain-model.adr.md`).

Rules:

- **No `ddl-auto` beyond `validate`.** Schema comes from Flyway, never from Hibernate.
- Relations default to `FetchType.LAZY`. An `EAGER` relation needs a stated reason.
- Optimistic locking (`@Version`) on any entity with concurrent status advancement.
- Native queries need a comment explaining why JPQL was insufficient.

### 3.3 Object storage

Signed documents go to S3-compatible storage behind a single `StorageClient` abstraction,
selected by `SIGNIUS_STORAGE_PROVIDER` (`MINIO` locally, `S3` in AWS). The application
never creates buckets in AWS — Terraform provisions them.

---

## 4. Configuration

- `src/main/resources/application.properties` plus `application-local.properties` and
  `application-dev.properties`.
- Secrets and environment-specific values come from environment variables, loaded from
  `.env` locally via `springboot3-dotenv`. **`.env` is never committed**; `.env.example`
  documents every variable.
- Typed access via `@ConfigurationProperties` classes in `<module>/config/property/`.
  `@Value` scattered through business classes is drift.
- **A new configuration variable MUST be added to `.env.example` in the same increment.**

---

## 5. Migrations (Flyway)

Location: `src/main/resources/db/migration/`. Applied on startup
(`spring.flyway.enabled=true`).

### 5.1 Naming — timestamp only

```
VYYYYMMDDHHmm__snake_case_description.sql
```

Verb-led prefixes: `create_`, `add_`, `alter_`, `rename_`.

```
V202607081000__add_kyc_partner_number.sql
V202608312130__scope_functionary_documents_to_company.sql
```

**Legacy sequential migrations `V1`–`V27` remain as-is for Flyway history. Never add a new
sequential migration** — it collides on parallel branches.

### 5.2 Rules

- One logical schema change per file.
- **Never edit a migration already applied in any shared environment.** Add a new one.
- Pick a timestamp that sorts after existing migrations.
- No manual schema changes in any environment.
- No `baseline-on-migrate` shortcuts without an ADR.
- A migration that adds a NOT NULL column to a populated table must supply a default or
  backfill in the same file.

Note: the DDL/DML/TCL/DCL classification some SDD projects use is **not** in force here.
The timestamp + verb convention above is the whole rule.

---

## 6. Testing Tooling

| Tool | Role |
|------|------|
| JUnit 5 (`useJUnitPlatform`) | Runner |
| `spring-boot-starter-test` | Spring context, MockMvc |
| `spring-boot-starter-graphql-test` | `GraphQlTester`, `ExecutionGraphQlServiceTester` |
| `org.mockito.kotlin:mockito-kotlin` 5.2.1 | Mocking — the project's collaborator-doubling tool |
| AssertJ (via boot starter) | Assertions |
| `com.networknt:json-schema-validator` 1.5.6 | CloudEvents envelope validation |
| JaCoCo 0.8.13 | Coverage **report**, not a gate |
| Playwright (`@playwright/test`) | API smoke/e2e against a running service |

Integration tests that need PostgreSQL guard on its availability rather than spinning a
container — see `onb/integration/PostgresAvailability.kt`. There is no Testcontainers
dependency.

Taxonomy, assertion rules and coverage expectations: `test.definition.md`.
When a test is written: `tdd.definition.md`.

---

## 7. Build & Quality Gate Commands

**Canonical gate list: `test.definition.md` § 7.** This document owns only the commands.

```shell
./gradlew spotlessApply     # format first — always before detekt
./gradlew spotlessCheck     # verify formatting
./gradlew detekt            # static analysis → build/reports/detekt/
./gradlew test              # unit + slice tests, finalized by jacocoTestReport
./gradlew build             # compile + check + assemble
npm run smoke               # Playwright smoke against a running service
npm run test:api            # full Playwright API suite
```

`lefthook` runs `validate-branch-name`, `spotlessApply` and `detekt` pre-commit,
`validate-commit-msg` on commit-msg, and `./gradlew test` pre-push.

---

## 8. Observability

- Actuator health at `/actuator/health` — the Playwright smoke test's target.
- Micrometer with OTLP and Prometheus registries; `docker-compose/otel/` runs a local
  collector and Prometheus.
- QES has AOP-based method metrics (`qes/metrics/aop/`).
- SQL logging is a local-debug switch, never a default.
- Logs must not leak personal data, tokens or provider payloads
  (`coding-style.definition.md` § 7).

---

## 9. Non-Goals (Technical)

- No microservice split — one deployable, one database
- No reactive stack
- No second persistence technology alongside JPA
- No H2 or in-memory database substitution for PostgreSQL
- No schema generation from entities
- No Testcontainers today (adding it is ADR trigger 2 and 3)
