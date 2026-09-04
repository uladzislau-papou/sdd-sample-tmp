# Architecture Definition

## 1. Purpose

This document is the **architecture reference** for Risk Management Service and is
intended to be used directly in Spec Driven Development (SDD):

- Specs and requirements **must map to these packages**.
- Every new component (entity, service, controller, provider client) must have a
  **clear home**.
- Dependency rules are explicit so they can be reviewed mechanically.

The goal is high orientation: when you see a class, you instantly know **why it exists**
and **where to look next**.

**This document describes the codebase as it is.** Where reality is untidy, the untidiness
is documented rather than wished away — see § 2.2 and § 11.3. A rule you can read in this
file is a rule the reviewer will enforce; an aspiration is labelled as one.

## 2. Architectural Style

### 2.1 What RMS is

A **modular monolith with a layered internal structure**.

- One Spring Boot application, one PostgreSQL schema, one deployment unit.
- Four feature modules — `qes`, `kyc`, `onb`, `boni` — plus `common`, `web` and `config`.
- Inside a module: `api` (delivery + outbound integration) → `service` (business logic
  and transactions) → `repository` (Spring Data JPA) → `domain` (entities, enums,
  exceptions).
- Domain classes are **JPA entities**. Persistence mapping and domain state are the same
  objects. See `modelling.definition.md` § 1.
- Business logic lives in `@Service` classes. Entities carry state, derived accessors and
  narrow helpers; they do not orchestrate.

### 2.2 What RMS is not (yet)

`README.md` § Project Structure states: *"We aim to strictly adhere to the Hexagonal
(Ports and Adapters) architecture."* The code does not do this, and has never done it:
there are no inbound or outbound ports, services depend on Spring Data repositories
directly, and the domain layer is Hibernate-annotated throughout.

**The layered structure in § 2.1 is the enforced rule. Hexagonal is a recorded direction
of travel, not a rule.** See `adr/0002-layered-module-architecture.adr.md` for the
decision and `adr/0010-hexagonal-as-target-architecture.adr.md` for the target and the
gap.

Consequences you must not draw from the README's stated aim:

- Do **not** introduce `inport`/`outport` packages into a module ad hoc. Partial
  hexagonality is worse than either endpoint — it doubles the ontology a reader has to
  hold.
- Do **not** report existing JPA-annotated domain classes as drift. They are the
  documented model.
- **Do** raise an ADR if you intend to migrate a module. That is § 6 trigger 1.

### 2.3 Pragmatic stance

- Spring is present at every layer, including `domain`. This is deliberate and is what
  `kotlin("plugin.jpa")` and `kotlin("plugin.spring")` in `build.gradle.kts` exist for.
- Provider-specific vocabulary is confined to `api/integration/<provider>/` and the
  mappers that translate it. It must not reach `service` or `domain` untranslated.
- Read and write paths are not split. A query service and a command service may share
  a repository.

## 3. Package Structure

Root: `com.jobradleasing.riskmanagementservice`

```
com.jobradleasing.riskmanagementservice
├── RiskManagementServiceApplication.kt      ← composition root / @SpringBootApplication
├── config/                                  ← application-wide config (OpenApiConfig)
├── web/                                     ← cross-module delivery concerns
│   ├── auth/                                ← GraphQL scope-enforcing interceptors
│   └── graphql/                             ← shared GraphQL HTTP response support
├── common/                                  ← shared kernel (§ 9)
│   ├── audit/                               ← CloudEvents, audit outbox, emit scheduler
│   ├── auth/                                ← OperatorContext, operator token decoding
│   ├── config/property/
│   ├── persistence/                         ← JPA converters
│   └── webhook/                             ← WebhookDelivery entity + repository
└── <module>/                                ← qes | kyc | onb | boni
    ├── api/
    │   ├── controller/                      ← GraphQL @Controller and REST @RestController
    │   ├── dto/                             ← outbound payload types
    │   ├── input/                           ← inbound payload types (@Argument, @RequestBody)
    │   ├── model/                           ← inbound wire models where they are not `input`
    │   ├── mapper/                          ← entity ↔ dto / provider-model translation
    │   └── integration/                     ← OUTBOUND provider clients (§ 4.6)
    │       └── <provider>/{client,model,error}/
    ├── service/                             ← business logic, transactions, schedulers
    │   └── <sub-domain>/                    ← e.g. kyc/service/screening
    ├── repository/                          ← Spring Data JPA interfaces
    ├── domain/
    │   ├── model/                           ← JPA @Entity classes
    │   ├── enums/
    │   └── exception/
    ├── statemachine/                        ← Spring Statemachine config (kyc only)
    ├── validation/                          ← Bean Validation constraints + annotations
    ├── audit/                               ← module-specific audit event builders
    ├── auth/                                ← module-specific principal / scope types
    ├── metrics/                             ← Micrometer AOP (qes only)
    ├── config/                              ← module Spring config
    │   └── property/                        ← @ConfigurationProperties
    ├── util/
    └── web/
        ├── advice/                          ← @ControllerAdvice / GraphQL error interceptors
        └── auth/                            ← servlet filters (JWT, scope enforcement)
```

A module uses the sub-packages it needs. Not every module has every one — only `kyc` has
`statemachine`, only `qes` has `metrics`.

## 4. Responsibilities by Package

### 4.1 `domain.model`

JPA entities. One class per table.

Contents:

- `@Entity` `data class` with `@Table(name = "...")`
- Column mapping, relations, `@Version` optimistic-lock counters
- `@CreationTimestamp` / `@UpdateTimestamp` audit columns
- Derived read-only accessors that answer a question about the row's own state

Rules:

- MUST be annotated with `jakarta.persistence` and MAY use `org.hibernate.annotations`.
  This is the model, not a violation.
- MUST NOT depend on `api`, `service` or `repository` of any module.
- MUST NOT perform IO, call repositories, or read the clock for business decisions.
- MUST NOT contain multi-entity orchestration. A method that needs a second aggregate
  loaded belongs in a service.
- Nullable columns MUST document what absence means in KDoc (`coding-style.definition.md`
  § 1.4).
- Optimistic locking (`@Version`) is REQUIRED on any entity whose status is advanced by
  more than one concurrent path — `KycCase` is the reference case.

Known exceptions (documented, not licensed to grow):
`kyc/domain/exception/KycNowNameScreeningOrderFailedException.kt`,
`qes/domain/SignatureInterface.kt`, `qes/domain/SignatureSessionStartResult.kt` and
`qes/domain/enums/ProcessStatus.kt` import from `api`/`service`. New occurrences are
drift.

### 4.2 `domain.enums` and `domain.exception`

- `enums` — closed value sets that appear in columns (`@Enumerated(EnumType.STRING)`),
  GraphQL schemas, or provider translation tables.
- `exception` — module-specific business failures.

**The base exception taxonomy lives in `qes.domain.exception` and is used by every
module** — `DomainException`, `BadUserInputException`, `EntityNotFoundException`,
`DomainErrorCode`. This is a historical accident: `qes` was the first module. It is
documented in § 11.3 as a known wart with a recorded intent to move to `common`; do not
duplicate the taxonomy per module in the meantime.

### 4.3 `repository`

Spring Data JPA interfaces.

Rules:

- One repository per aggregate-shaped entity; not one per table where tables are
  child rows of a parent the parent already manages.
- Derived query methods and `@Query` are both acceptable. A query whose intent is not
  obvious from its name MUST carry KDoc stating the selection criterion.
- MUST NOT be injected into a controller (§ 6 rule 1 — currently true with zero
  exceptions; keep it that way).
- Pagination goes through `Pageable`, bounded by `common.config.property.PaginationProperties`.

### 4.4 `service`

Business logic and the transaction boundary. This is where a use case lives.

Responsibilities:

- Load entities via repositories
- Apply business rules and state transitions
- Persist
- Emit audit events and enqueue outbound deliveries
- Own `@Transactional`

Rules:

- `@Transactional(rollbackFor = [Exception::class])` on the public entry point of a
  state-changing operation. Read-only queries use `@Transactional(readOnly = true)` where
  a consistent multi-read snapshot matters.
- **A service is the only place that may open a transaction.** Known exceptions today:
  `qes/config/SignatureWebhookTransactionDedup.kt`, `qes/config/StatusChangeListener.kt`,
  `common/audit/AuditOutboxAppender.kt`, `onb/api/mapper/OnbPartiesAssembler.kt`.
  New occurrences outside `service` are drift.
- MUST NOT be annotated `@Controller`, `@RestController` or carry HTTP/GraphQL mapping
  annotations.
- `@Scheduled` belongs on a dedicated `*Scheduler` class that delegates to a `*Service` —
  see `WebhookDeliveryScheduler`, `AuditEmitScheduler`, `OnbProgressDeliveryScheduler`,
  `KycRadarDeliveryScheduler`, `KycResearchPollScheduler`. A scheduler contains no
  business logic.
- A service MUST NOT swallow a provider failure into a success path. Translate it into a
  domain exception or a recorded failure state.

### 4.5 `api.controller`, `api.dto`, `api.input`, `api.mapper`

Delivery. Two flavours:

| Flavour | Annotation | Surface |
|---------|-----------|---------|
| GraphQL | `@Controller` + `@QueryMapping` / `@MutationMapping` / `@SchemaMapping` | `/riskmanagement/{qes,kyc}/v1/graphql` |
| REST | `@RestController` + `@RequestMapping` | ONB API, provider webhooks, RADar webhook |

Rules:

- Controllers **map, validate and delegate**. No business rules, no repository access,
  no transaction management.
- Inbound types live in `api.input` (or `api.model` for pure wire models such as
  `boni/api/model/PartnerDecisionWebhookRequest.kt`), outbound types in `api.dto`.
- **An entity MUST NOT be returned from a controller.** Map to a `*Dto`.
- `@Valid` + Bean Validation constraints on input types; `@Validated` on the controller.
- Every GraphQL field a controller exposes MUST exist in the matching `.graphqls` schema
  under `src/main/resources/graphql/`. The schema is part of the contract.
- Mappers are stateless. A mapper that needs a repository is a service.

### 4.6 `api.integration` — outbound provider clients

**This is where outbound calls live, despite sitting under `api`.** The name is
misleading and is recorded as a wart in § 11.3; the location is nonetheless the
convention and new provider clients follow it.

Contents per provider:

```
api/integration/<provider>/
├── client/     ← RestClient wrappers
├── model/      ← the provider's request/response shapes, verbatim
└── error/      ← provider error decoding
```

Rules:

- Provider models are **the provider's vocabulary** and stay here. A provider model
  MUST NOT appear in a service signature, an entity, or a DTO — translate in
  `api.mapper`.
- HTTP concerns — base URL, auth headers, timeouts, retry, status decoding — are confined
  to the client.
- Configuration comes from `@ConfigurationProperties` in `config/property/`, never from
  `@Value` scattered through the client.
- A provider failure surfaces as a typed exception from `domain.exception`, never as a
  raw `RestClientException` reaching a service.

### 4.7 `web` (module-level) — filters and error translation

- `web/auth/` — servlet filters that authenticate and enforce scopes
  (`OnbScopeEnforcingFilter`, `WebhookJwtAuthFilter`).
- `web/advice/` — `@ControllerAdvice` REST handlers and GraphQL error interceptors
  (`GraphQlControllerAdvice`, `KycGraphQlErrorInterceptor`, `OnbRestExceptionHandler`).

Rules:

- Error translation maps a domain exception to a status/error classification. It MUST NOT
  contain business logic and MUST NOT leak stack traces or provider payloads.
- A new externally reachable surface MUST declare its scope requirement here or in the
  root `web/auth/` interceptors. An unprotected new endpoint is drift.

### 4.8 Root `web/` and `config/`

- `web/auth/` — `OperationScopeEnforcingGraphQlInterceptor`,
  `PathAwareScopeEnforcingGraphQlInterceptor`: the cross-module GraphQL scope gate.
- `web/graphql/` — shared GraphQL HTTP response helpers.
- `config/OpenApiConfig.kt` — springdoc configuration.

These are cross-module by nature. Module-specific behaviour does not belong here.

### 4.9 `RiskManagementServiceApplication`

Composition root. `@SpringBootApplication`, component-scanning the root package so
modules are discovered automatically. No business logic.

## 5. Command vs Query in SDD Terms

RMS does not split read and write models. The mapping from spec intent to code is:

**Commands** (state change):
spec says *"when X happens, the system must…"* →
`api.controller` (mutation / POST) → `service` (`@Transactional`) → entity mutation →
`repository.save` → audit event + outbound enqueue.

**Queries** (read-only):
spec says *"the system shows / lists / returns…"* →
`api.controller` (query / GET) → `*QueryService` (`@Transactional(readOnly = true)`) →
repository → mapper → `*Dto`.

Guiding rule: if the operation advances a lifecycle status, it is a command and goes
through the owning service even if it looks like a read.

## 6. Dependency Rules (Enforceable)

These are checked on every increment. Each is currently true unless an exception is
named.

1. **No controller injects a repository.** Currently zero violations.
2. **No entity is returned across an API boundary.** Controllers return `*Dto` types.
3. **`domain` does not import `api` or `service`** — four documented exceptions in § 4.1.
4. **Transactions are opened in `service`** — four documented exceptions in § 4.4.
5. **Provider models do not escape `api.integration`.** No `…integration.<provider>.model`
   type in a service signature, entity, DTO or GraphQL schema.
6. **Every module may depend on `common`.** `common` depends on no feature module.
7. **Cross-module dependencies are permitted but registered** — see § 11.2. A cross-module
   import in a direction not listed in § 11.2 is drift.
8. **Every externally reachable endpoint is scope-protected**, or its openness is
   documented (actuator, provider webhooks — which authenticate by other means).

## 7. Practical "Where does this go?" Cheatsheet

| New thing | Home |
|-----------|------|
| New table | `domain/model/` entity + Flyway migration (`technical.spec.md` § 5) |
| New closed value set | `domain/enums/` |
| New business rule / orchestration | `service/` (or `service/<sub-domain>/`) |
| New query | `repository/` method + a `*QueryService` |
| New GraphQL operation | `.graphqls` schema + `api/controller/` + `api/input/` + `api/dto/` |
| New REST endpoint | `api/controller/` `@RestController` + scope rule in `web/auth/` |
| New provider call | `api/integration/<provider>/client/` + `model/` + a mapper |
| New provider webhook | `api/controller/<provider>/` + dedup + `service/webhook/` |
| New scheduled job | `service/<Name>Scheduler` delegating to `<Name>Service` |
| New outbound delivery to an external system | an outbox table + a `*DeliveryService` + a `*DeliveryScheduler` |
| New config knob | `config/property/` `@ConfigurationProperties` + `.env.example` |
| New cross-module utility | `common/` — and only if two modules genuinely need it |

## 8. Time, Identity and Audit

### 8.1 Time

- Entities MUST NOT call `Instant.now()` to make a business decision. Creation and update
  stamps via `@CreationTimestamp` / `@UpdateTimestamp` are fine — those are row metadata,
  not decisions.
- A service reads the clock, or takes the timestamp from the event that caused the change.
- **A timestamp asserted by an external caller is not authoritative** unless the caller is
  the system of record for that fact. A provider webhook's `occurredAt` is authoritative
  for when the provider acted; a client's claim about when *we* acted is not.

### 8.2 Operator identity

Operator attribution comes from `common.auth.OperatorContext`, populated by
`OperatorContextFilter` from the platform `access_token` cookie, which is **decoded
without verification** — the AWS-internal auth gateway has already validated it
(`README.md` § Authentication).

- A regulated state change MUST record the operator (`updatedByName` / `updatedByEmail`)
  when one is present.
- Absent an operator — schedulers, webhooks, ONB — attribution is
  `svc:risk-management-service` (`common.audit.AuditActors`).
- A service MUST NOT take the acting operator from request input. `input.actor` is not
  the operator; `OperatorContext` is. `KycCaseDecisionService` is the reference.

### 8.3 Audit

Regulated actions emit a CloudEvent through `common.audit`:
`AuditEventPublisher` → `AuditOutboxAppender` → `audit_outbox` table →
`AuditEmitScheduler` → `AuditEmitService`.

- The write to the outbox joins the business transaction. Emission is separate and
  retried. This is deliberate: an audit event must not be lost, and must not fail the
  business operation.
- Event names and payload keys are catalogued in `docs/kyc/kyc-event-catalogue.md` and
  `docs/audit-kernel.md` in the service repository.
- Personal data in audit payloads is hashed (`common.audit.EmailHasher`) or omitted.

## 9. `common` — the shared kernel

Cross-cutting building blocks owned by no feature module.

```
common
├── audit/          ← CloudEvents envelope, outbox entity, appender, emit scheduler
├── auth/           ← OperatorContext, OperatorPrincipal, OperatorTokenService, filter
├── config/property ← PaginationProperties
├── persistence/    ← JsonNodeConverter
└── webhook/        ← WebhookDelivery entity + status + repository
```

Rules:

- Keep `common` minimal. The test is **ownership, not usage** — "two modules call it" is
  not sufficient; the question is whether either module owns the concept.
- `common` MUST NOT import from `qes`, `kyc`, `onb` or `boni`. Currently true.
- A module-specific type in `common` is drift even though `common` is registered.

**Known gap:** the base exception taxonomy that belongs here lives in `qes.domain.exception`
(§ 4.2, § 11.3).

## 10. Anti-patterns (explicitly forbidden)

- Business logic in a controller, a mapper, or a `@ControllerAdvice`
- A repository injected into a controller
- An entity returned from a controller, or accepted as an input type
- A provider model (`…integration.<provider>.model.*`) in a service signature or DTO
- A raw provider exception escaping `api.integration`
- Swallowing a provider error into a success response
- `@Transactional` on a private method or a self-invoked method (Spring proxies will not
  apply it — a silent correctness bug)
- A new externally reachable endpoint with no scope requirement
- A new top-level package under the root that is not registered in § 11.1
- A cross-module import in a direction not registered in § 11.2
- Schema changes made outside Flyway, or an applied migration edited in place
- Introducing `inport`/`outport` packages into one module without the ADR in § 2.2
- Personal data written to logs or unhashed into audit payloads

## 11. Registry

### 11.1 Registered top-level packages

Authoritative registry of packages directly under
`com.jobradleasing.riskmanagementservice`. A package not in this table is drift.

| Package | Kind | Owns | ADR |
|---------|------|------|-----|
| `qes` | Feature module | Identifications and signature sessions across IDnow, PostIdent and Signius: session lifecycle, provider strategies, webhooks, client callbacks, signed-document storage | `adr/0001-technical-stack.adr.md` (original module) |
| `kyc` | Feature module | KYC cases and their state machine: companies, core data, functionaries, UBOs, screening, research, transparency register, case documents, operator decisions | `adr/0007-kyc-case-state-machine.adr.md` |
| `onb` | Feature module | Onboarding integration with RADar: company intake, parties collection, progress delivery, decision delivery, research polling | `adr/0009-outbox-backed-outbound-delivery.adr.md` |
| `boni` | Feature module | Partner credit decisions received by webhook | — (introduced with the RADar webhook) |
| `common` | Shared kernel | Audit outbox and CloudEvents, operator context, webhook delivery, JSON persistence converter, pagination properties. Not a feature module. See § 9. | `adr/0008-audit-outbox-cloudevents.adr.md` |
| `web` | Cross-module delivery | GraphQL scope-enforcing interceptors, shared GraphQL HTTP response support | `adr/0006-jwt-scope-authorization.adr.md` |
| `config` | Application config | springdoc/OpenAPI configuration | — |

**Adding a row requires an ADR** (`sdd.playbook.md` § 6 item 9).

### 11.2 Registered cross-module dependencies

Modules are **not** isolated. Pretending otherwise would make this document false, so the
real graph is registered instead. Measured from `import` statements in
`src/main/kotlin`:

| From → To | Volume | What it is | Verdict |
|-----------|--------|-----------|---------|
| `* → common` | ~80 | Audit, operator context, webhook delivery | **Intended** |
| `kyc → qes` | ~80 | Almost entirely the base exception taxonomy (§ 4.2), plus `GeneralUtil`, a country-code constraint, auth principal | **Wart** — see § 11.3 |
| `onb → kyc` | ~117 | Direct use of `kyc` enums, entities, repositories and services | **Accepted, bounded** — see below |
| `onb → qes` | ~23 | Exception taxonomy, shared utilities | **Wart** |
| `boni → qes` | ~6 | Exception taxonomy | **Wart** |
| `qes → kyc` | 4 | Narrow | Tolerated |
| `qes → onb` | 2 | Narrow | Tolerated |
| `kyc → onb` | 1 | Narrow | Tolerated |
| `web → kyc`, `web → qes` | 4 | Scope enforcement needs module scope constants | Intended |

**On `onb → kyc`.** `onb` is deliberately a *consumer* of `kyc`: it exists to project KYC
state outward to RADar. It reaches into `kyc` repositories and entities directly rather
than through a published interface. This is accepted because the two modules ship
together and version together, and because introducing a facade would add indirection
without reducing coupling.

What is **not** accepted, and is drift:

- `kyc` acquiring a dependency on `onb` beyond the single existing import. Delivery is
  `onb`'s job; `kyc` publishes state, it does not know its consumers.
- `onb` **writing** `kyc` state outside a `kyc` service. Reading `kyc` state is the
  contract; mutating a `KycCase` from `onb` is not.

A new edge not in this table requires the table to be updated and the reason recorded.
A new edge that inverts an existing direction requires an ADR (§ 6 trigger 10).

### 11.3 Registered warts

Recorded so they are neither rediscovered as findings every review nor allowed to spread.
Each has an owner-decision status.

| Wart | Where | Why it is not fixed today | Rule while it stands |
|------|-------|---------------------------|----------------------|
| Base exception taxonomy lives in `qes` | `qes/domain/exception/` | Moving it touches ~110 imports across four modules; mechanical but wide | Use it as-is. Do **not** create a parallel taxonomy in another module. Moving it to `common` is its own increment |
| Outbound clients live under `api/integration` | every module | The name predates the outbound clients; renaming is a wide, purely mechanical move | New provider clients go there anyway — consistency beats a half-rename |
| `qes/validation/annatation/` is misspelled | `qes/validation/` | Package rename touches every constraint import | Do not create new packages under the misspelling; a new constraint goes in a correctly spelled sibling |
| `domain` imports `api`/`service` in four files | § 4.1 | Each needs its own small refactor | New occurrences are drift |

Adding a row to this table is not a way to legalise new drift. A wart is something that
**already exists and is bounded**; new code does not get to join the list.

### 12. Growing the architecture

The pressure to add a top-level module usually means one of three things. Only the third
justifies a new row in § 11.1:

- A new **operation** on an existing concept → the existing module.
- A new **integration with an existing provider** → `api/integration/<provider>/` in the
  module that owns the concept.
- A genuinely different **business capability** with its own lifecycle, its own external
  consumers and its own vocabulary → a new module, with an ADR making that argument.
