# Architecture Definition

## 1. Purpose

This document is the **architecture reference** for this repository and is intended to be used directly in **Spec Driven Development (SDD)**:

- Specs and requirements **must map to these packages**.
- Every new component (domain type, use case, adapter) must have a **clear home**.
- Dependency rules are explicit so they can be enforced (e.g., via ArchUnit).

The goal is **high orientation (“coding mise en place”)**: when you see a class, you instantly know **why it exists** and **where to look next**.

## 2. Architectural Style

We use **Hexagonal Architecture (Ports & Adapters)**.

Key concepts:

- **Core**: domain + ports (stable, technology-agnostic).
- **Inbound side**: triggers the core (delivery concerns).
- **Outbound side**: implements what the core needs from external systems (integration concerns).
- **Bootstrap**: composition root (wiring only).

Pragmatic stance:

- The **core stays framework-free**.
- Framework usage is allowed in **drivers** and **adapters**, but must not leak into the core.
- We separate **commands** (state changes) from **queries** (read-only) in a way that keeps boundaries clear and SDD-friendly.

## 3. Package Structure

```
com.example.contractmanagement                ← shared root
├── ContractManagementApplication            ← entry point; see § 4.9 for why it is here
├── bootstrap                                ← composition root (cross-context)
│   └── <ContextName>Config
├── shared                                   ← shared kernel (cross-context building blocks)
│   ├── domain                               ← see § 9
│   │   └── event
│   │       └── DomainEvent                  ← marker interface for all domain events
│   ├── outport                              ← ports needed by more than one context
│   └── outbound                             ← adapters implementing shared.outport
└── <bounded-context>                        ← one sub-package per bounded context; § 11 is the registry
    ├── core
    │   ├── domain
    │   │   └── <aggregate>                  ← one sub-package per aggregate root (e.g. master)
    │   │       ├── event                    ← domain events for this aggregate
    │   │       └── exception                ← domain exceptions for this aggregate
    │   ├── inport
    │   │   ├── command                      ← input data carriers (records)
    │   │   ├── result                       ← output data carriers (records)
    │   │   └── usecase                      ← use case interfaces (inbound ports)
    │   └── outport
    ├── inbound
    │   ├── driver
    │   ├── listener                         ← Spring @TransactionalEventListener / @EventListener hooks
    │   └── rest
    │       ├── request                      ← inbound HTTP body / parameter DTOs
    │       └── response                     ← outbound HTTP body DTOs
    └── outbound
        ├── persistence
        │   ├── write
        │   └── read
        └── integration
```

## 4. Responsibilities by Package

### 4.1 `core.domain`

Contains the pure domain model:

- Aggregates, Entities, Value Objects
- Domain Services (pure business logic only)
- Domain Events (types only)

Rules:

- MUST NOT depend on any other project package.
- MUST NOT use Spring / Jakarta / Jackson / Swagger / persistence frameworks.
- MUST NOT perform IO, remote calls, DB access, or message publishing.
- MAY use code generation helpers (e.g., Lombok) only if allowed explicitly (ADR).

### 4.2 `core.inport`

Defines the **application boundary** as interfaces and data carriers. Split into three sub-packages:

| Sub-package | Contents | Naming convention |
|-------------|----------|-------------------|
| `inport.command` | Input data carriers (immutable records) | `*Command` |
| `inport.result` | Output data carriers (immutable records) | `*Result` |
| `inport.usecase` | Use case interfaces (inbound ports) | `*UseCase` |

Rules:

- Framework-free.
- Must not reference adapters (no REST DTOs, no jOOQ records).
- `usecase` interfaces may only reference types from `inport.command`, `inport.result`, and `core.domain.<aggregate>.exception`.
- Inport types are stable contracts: keep them small and intention-revealing.

### 4.3 `core.outport`

Defines what the core needs from the outside world as interfaces:

- Repositories (write-side)
- External gateways (e.g., fraud check, pricing, identity)
- Notification ports (email, messaging)
- Event publishing ports
- Persistence/query access ports (read-side), if read concerns are accessed via ports

Rules:

- Framework-free.
- No implementation logic.
- “Outport” means: the core owns the abstraction, adapters provide implementations.

### 4.4 `inbound.driver`

Contains **use case implementations** (application services).

They orchestrate:

- Loading aggregates via outports
- Executing domain logic
- Saving via outports
- Triggering side effects via outports
- Transaction boundaries

Framework stance:

- Spring is allowed here to avoid configuration/bean boilerplate and to support transactions.

Allowed examples:

- `@Service`
- `@Transactional`
- `@Validated` (optional)

Forbidden examples:

- `@RestController`, `@Controller`
- `@KafkaListener`, `@RabbitListener`
- `@Scheduled`
- Direct usage of concrete adapter classes (`outbound.*` classes)

Dependency rules:

- MAY depend on: `core.domain`, `core.inport`, `core.outport`
- MUST NOT depend on: `inbound.rest`, `outbound.*` implementations, other adapters

### 4.5 `inbound.rest`

Contains delivery concerns only. Every resource is represented by two types:

| Type | Naming | Sub-package | Responsibility |
|------|--------|-------------|----------------|
| `*RestAPI` | interface | `rest` | HTTP contract: routes, methods, status codes, parameter bindings (`@RequestMapping`, `@PostMapping`, `@Valid`, `@ResponseStatus`, …) |
| `*Controller` | class | `rest` | Web adapter: implements `*RestAPI`, maps DTOs to commands, delegates to inport. **No HTTP annotations.** |
| `*Request` | record | `rest.request` | Inbound HTTP body / parameter DTOs |
| `*Response` | record | `rest.response` | Outbound HTTP body DTOs |
| `*ExceptionHandler` | class | `rest` | Maps domain exceptions to HTTP error responses |

Rules:

- All Spring MVC annotations (`@RequestMapping`, `@PostMapping`, `@DeleteMapping`, `@PatchMapping`, `@ResponseStatus`, `@RequestBody`, `@PathVariable`, `@Valid`) belong on the `*RestAPI` interface.
- `*Controller` carries only `@RestController`, `implements *RestAPI`, and `@Override` methods.
- Controllers MUST depend only on `core.inport` (interfaces), not on implementations.
- MUST NOT access `core.domain` directly for API contracts (no domain objects in public DTOs).
- MUST NOT access repositories or outbound implementations.
- Mapping responsibility:
    - RequestDTO → Command/Query input
    - Result/Read data → ResponseDTO

### 4.6 `outbound.persistence`

Contains DB implementations and data access.

Important: we split **read** and **write** for clarity of intent and to keep the mental model consistent in SDD.

#### `outbound.persistence.write`

Write-side persistence:

- Implements repository outports used by commands/use cases
- Loads aggregates and persists changes
- Responsible for mapping between DB representation and domain model

Rules:

- MAY depend on: `core.domain`, `core.outport`
- MUST NOT depend on: `inbound.*`
- MUST NOT expose persistence types into the core (no jOOQ records / JPA entities in ports)

Typical characteristics:

- Transaction-aware (but transaction boundary is owned by driver/use case)
- Focused on **consistency and invariants** (aggregate boundaries)

##### Selection criteria in write-side queries

A write-side query MAY filter on a domain criterion — `findActiveByCustomerNumber` rather
than `findByCustomerNumber` plus a status filter in the caller. Three conditions:

1. **The aggregate still enforces the rule.** The query *selects candidates*; it does not
   replace the guard. `addContract` must reject an inactive Master whether or not the query
   already excluded it. A query that becomes the only enforcement is drift.
2. **The criterion is named in the method.** `findActiveByCustomerNumber`, not
   `findByCustomerNumberAndStatus(status)` with the constant supplied by the caller — that
   just relocates the knowledge to the caller.
3. **It is specified.** The port spec states the criterion, so the duplication between SQL
   and aggregate guard is deliberate and visible rather than discovered later.

Why permit the duplication at all: the alternative is a status filter in the caller, and for
an inbound adapter that is business logic in the wrong layer (§ 4.8).

The duplication is the accepted cost. If the criterion changes, both places change, and the
port spec is where that is recorded.

**This rule currently has no subject.** `MasterRepository` exposes `findById` only
(`ports/master-repository.outport.spec.md`), because no use case selects a set. The rule is
kept because the first query that does select one is exactly when nobody will want to stop and
derive it.

> The rule once existed only as an argument inside a port spec — an authority-level-16
> document, which made the precedent unappealable and unenforceable. It was raised to
> definition level after `ddd-hex-reviewer` reported it as the last remaining undocumented
> rule. That is the part worth remembering: where a rule *lives* decides whether it can be
> cited against a future increment.

#### `outbound.persistence.read`

Read-side persistence:

- Implements **read-only** access patterns optimized for queries (projections, joins, denormalized views)
- Does **not** load aggregates to answer queries
- Can return read models / projection DTOs

What is “read” here?

- Any access that is **side-effect free** and does not require domain invariants to be enforced.
- Queries that are best served by SQL/jOOQ projections rather than aggregate reconstruction.

What belongs here:

- “List / search / overview” queries
- “Detail view” queries that join multiple tables
- Analytics-style or reporting queries (within reason)

Rules:

- MAY depend on: `core.outport` (if query ports exist), and optionally shared identifiers/value types from `core.domain` (e.g., IDs)
- MUST NOT call write repositories.
- MUST NOT perform domain mutation.
- If it returns DTOs, those DTOs MUST NOT be used as public REST DTOs without explicit mapping in `inbound.rest` (to keep API stable).

### 4.7 `outbound.integration`

Outbound adapters for external systems:

- HTTP clients
- Messaging publishers
- File stores, 3rd-party APIs

Rules:

- Implements `core.outport` interfaces.
- MAY depend on `core.domain` for domain types (careful: do not leak them over the wire).
- Must keep protocol-specific details here (serialization, headers, retries, etc.).

### 4.8 `inbound.listener`

Inbound adapters driven by Spring's internal event infrastructure (`@TransactionalEventListener`, `@EventListener`).
These are inbound because they receive a trigger and drive the application — the trigger source happens to be
the Spring `ApplicationEventPublisher` rather than an HTTP request or external message broker.

For future external message consumers (Kafka, RabbitMQ), use `inbound.consumer` or `inbound.messaging`.

Rules:

- Must not contain business logic; forward into inports or outports as appropriate.
- If a listener triggers business behaviour, it should call an inport (not a concrete use case class).
- Must not access `outbound.*` implementations directly.

### 4.9 `bootstrap` and the entry point

Composition root and wiring. Lives at the **shared root level**, **outside any
bounded-context package**. This reflects that bootstrap is not aligned to any single
context — it wires the whole application.

Contains:
- Per-context configuration classes (e.g. `ContractConfig`)
- Bean wiring

**The Spring Boot entry point is not in `bootstrap`. It sits in the root package itself**,
and two rules depend on that:

- Spring's component scan defaults to the entry point's own package. From `bootstrap` —
  a *sibling* of every context — the default scan would find no context at all, which is
  why the earlier layout needed an explicit `scanBasePackages` literal to keep in sync.
- The architecture tests derive their package root from this class
  (`ArchitectureRoot`), so a package rename cannot leave a stale string behind. Three
  tests previously repeated the root as a literal; a rename would have left three copies
  that still compiled and still passed, checking nothing.

Wiring stays in `bootstrap`, so the composition root is still one identifiable place. The
entry point is not wiring — it is the position the framework and the gates both read the
package root from.

Rules:

- This package may depend on everything else.
- No business logic.
- No domain logic.
- Only wiring and configuration.
- The entry point needs no `scanBasePackages`: sitting in the shared root, Spring's
  default scan discovers every bounded context. A literal here is a second source of
  truth for something the class's own position already states.

## 5. Command vs. Query in SDD Terms

In SDD we need a stable mapping from “spec intent” to implementation.

Commands (state change):

- Spec talks about: “When X happens, system must…”
- Implementation lives in: `inbound.driver` (use case) + `core.domain` (business rules) + `outbound.persistence.write` (repo)

Queries (read-only):

- Spec talks about: “System shows/list/returns …”
- Implementation lives in: `inbound.rest` (mapping/API) + `outbound.persistence.read` (projection/query)  
  and optionally `core.inport`/`core.outport` if you model query ports

Guiding rule:

- If the behaviour must respect domain invariants via aggregate logic → treat it as command-side (load aggregate).
- If it is purely informational and best served as projection → treat it as read-side (no aggregate load).

## 6. Dependency Rules Summary (Enforceable)

1. `core.domain` depends on nothing else.
2. `core.inport` and `core.outport` are framework-free.
3. `inbound.rest` depends only on `core.inport`, its own DTOs, and
   `core.domain.<aggregate>.exception` — the last solely so `*ExceptionHandler` can map
   domain exceptions to HTTP statuses, which § 4.5 assigns it as its job. It must not
   depend on `core.outport`, on `inbound.driver`, or on any `outbound.*` implementation.
   The § 4.5 prohibition on `core.domain` is about **API contracts**: no domain object in
   a public DTO.
4. `inbound.driver` depends on `core.domain`, `core.inport`, `core.outport` only.
5. `outbound.*` packages implement `core.outport` and must not be referenced as concrete types from drivers/controllers.
6. Persistence types must not cross boundaries into the core (no jOOQ/JPA types in ports).
7. Only `bootstrap` wires implementations; it contains no business logic.

## 7. Practical “Where does this go?” Cheatsheet

- New Aggregate / VO / Domain rule → `core.domain`
- New Use Case interface → `core.inport.usecase`
- New Command / Result type → `core.inport.command` / `core.inport.result`
- New Use Case implementation (orchestration) → `inbound.driver`
- New Repository interface / external dependency abstraction → `core.outport`
- New REST contract (routes, status codes) → `inbound.rest` `*RestAPI` interface
- New REST implementation (delegation) → `inbound.rest` `*Controller` class
- New DB mapping for aggregates (write) → `outbound.persistence.write`
- New query/projection (read) → `outbound.persistence.read`
- New external API client / publisher → `outbound.integration`
- New wiring/config → `bootstrap`

## 8. Time, randomness, and “now”
- Never call Instant.now() in the domain.
- Use an outbound port ClockPort or provide time from application service.

### 8.1 Who may supply a domain timestamp

The rule above says where `now()` may be read. It does not say who is allowed to *decide*
what "now" was, and that gap let three use cases accept an arbitrary `Instant` from an
HTTP client with nothing bounding it — an event could be dated before the record it referred
to existed, or years in the future.

**A driver behind a REST or GraphQL endpoint MUST read the timestamp from `ClockPort`. It
MUST NOT accept one from the request.** The time an action happened is the system's
observation, not the caller's claim, and there is no business case in this project for a
client asserting it. Nothing needs validating, because nothing is accepted.

**A driver receiving a timestamp from another bounded context MUST use the one supplied,
falling back to `ClockPort` only when it is absent.** Such a timestamp is a fact already
recorded in the originating context, crossing a boundary. Re-dating it with the receiver's
clock would make the two records drift apart by the event's delivery latency, and neither
would look wrong on its own.

This half of the rule has **no subject today**: there is one bounded context (`adr/0024`) and
no cross-context event. The service's earlier scope had three use cases relying on it, which is
why it is written rather than inferred. The first half — a REST or GraphQL client may not
supply one — is live, and `TimestampRulesTest` enforces it.

The discriminator is the **caller**, not the field:

| Inbound adapter | Timestamp source |
|-----------------|------------------|
| `inbound.rest` → driver | `ClockPort` only. No timestamp on the request DTO |
| `inbound.graphql` → driver | `ClockPort` only. No timestamp field on the GraphQL input type |
| `inbound.listener` → driver | the event's timestamp, `ClockPort` as fallback |
| another context's driver → inport | the caller's timestamp, `ClockPort` as fallback |

A command reached from both surfaces therefore carries a nullable timestamp, and the REST
adapter simply never populates it.

**The GraphQL row was added by UC07's OPEN QUESTION 11, and the reasoning is worth keeping
because the row alone does not carry it.** The table originally enumerated `inbound.rest`
only, and `adr/0020-graphql-as-the-only-transport.adr.md` then made GraphQL the *sole*
transport — so the one rule about who may supply a timestamp named the one adapter the
service no longer had. Nothing was violated; the rule simply stopped reaching anything.

It is a row and not a rewrite because the discriminator this section already states is **the
caller**, not the field. A GraphQL client and a REST client are the same kind of caller: an
external party asserting a fact about our system's clock. Reading the existing rule as
covering GraphQL was always the sensible reading — but a reading is not enforceable, so the
row now has an executable owner: `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes`
fails if a type in `inbound.rest.request` or `inbound.graphql` declares an `Instant` field.
It is keyed to both adapter packages rather than to `inbound.rest` alone, which is what
stops the next transport from repeating this gap (ADR-0014).

Recorded after `ddd-hex-reviewer` reported the gap under `Undocumented` during the UC08
review: `architecture.definition.md` § 8 permitted "provide time from application
service" without saying whether an inbound adapter may override the clock, so three use
cases had quietly answered yes.

## 9. Shared Kernel (`shared`)

Cross-cutting building blocks that are not owned by any single bounded context.

```
shared
├── domain
│   ├── <ForeignId>               ← identity owned by no context (ADR 0005 category 3) — none today
│   └── event
│       ├── DomainEvent           ← marker interface
│       └── <CrossContextEvent>   ← none today: one context, no cross-context event
├── outport                       ← ports every context needs
│   ├── ClockPort
│   └── DomainEventPublisher
└── outbound                      ← adapters implementing shared.outport
    ├── clock
    └── integration
```

### `shared.domain` and `shared.domain.event`

- `DomainEvent` — the marker interface all domain events implement.
- Cross-context integration events, so a consuming context need not depend on the
  publishing one (see § 11 rule 3 and ADR 0002). **There are none**: the service has one
  bounded context.
- Identities owned by no context in this system (ADR 0005 category 3). **There are none**, and
  ADR-0005's reservation — a second member requires an ADR — is therefore unspent.

`DomainEvent` is currently the shared kernel's only `domain` member.
- Framework-free; no Spring, no IO.

### `shared.outport`

Ports the core of **more than one** context needs, where neither context owns the
abstraction — `ClockPort`, `DomainEventPublisher`.

A port used by exactly one context belongs in that context's `core.outport`, not here.

### `shared.outbound`

**Adapters implementing `shared.outport` live here, not inside a bounded context.**

Rationale: an implementation of a shared port is not owned by any context either. This was
found the expensive way — `ClockPort`'s adapter once lived inside one context's
`outbound.integration`, which forced a second context to obtain its clock from the first
context's `@Configuration`. No import crossed a boundary, so no compile-time check could see
it, and the claim that the two contexts depended only on `shared.domain` was simply false.

Wiring: `bootstrap.SharedConfig` declares the shared beans. A per-context config declares only
its own context's adapters.

**Framework dependencies are permitted in `shared.outbound`** (and in a future
`shared.inbound`), on the same terms as any other adapter package — § 4.7. These are
adapters, so Spring, HTTP clients and messaging libraries belong here.
`LoggingDomainEventPublisher` wrapping Spring's `ApplicationEventPublisher` is the
intended shape.

The framework-free constraint applies to `shared.domain` and `shared.outport`, exactly as
it applies to a context's `core`. The boundary is core-versus-adapter, not
shared-versus-context: `shared` is not a privileged framework-free island, it is a
context-neutral one.

Rules:
- Keep `shared` minimal. Only add here what is genuinely cross-context.
- Do not add context-specific types here (e.g. `MasterCreated` stays in
  `contract.core.domain.master.event`).
- `shared` must not depend on any bounded context. Any context may depend on `shared`.
- The test is **ownership, not usage**. "Both contexts use it" is not sufficient — if one
  context owns it, the other goes through an event or its own port.
- A context MUST NOT wire another context's beans. A shared adapter comes from
  `SharedConfig`, never from another context's config.

## 10. Anti-patterns (explicitly forbidden)
- JPA annotations in domain objects.
- Calling repositories from aggregates/entities/value objects.
- Using primitives for domain concepts when they carry meaning (money, ids, email, etc.).
  **Scoped for identities:** this applies to identities the context *owns*. A reference to
  an identity owned by another bounded context MAY be a plain `String` — see
  `modelling.definition.md` § Identity and `adr/0005-bounded-context-identity-boundaries.adr.md`.
- Cross-aggregate invariants enforced inside one transaction “because it’s convenient”.
- Introducing a top-level package that is not registered in § 11.

### Multiple aggregates in one transaction — guideline, not rule

Updating several instances of the **same** aggregate type in one transaction is
**permitted**. The forbidden thing above is narrower: enforcing an *invariant that spans
aggregates* inside one transaction, which couples their consistency boundaries.

Guideline: prefer one aggregate per transaction. It bounds lock duration and keeps
failures isolated.

No current use case departs from the guideline. Every one of the six touches exactly one
`Master`, and a Master's contracts are inside it rather than beside it — so "several
aggregates in one transaction" does not arise. UC04 is the one that looks like an exception
and is not: deleting a Master removes its contracts because they are *part of* that aggregate,
not because two aggregates are being coordinated.

The earlier scope did depart from it, in a way worth keeping: a listener activated every
eligible child of one parent in a single `REQUIRES_NEW` transaction, accepted because no
invariant spanned them and each mutation was independent — a fan-out, not a coordination. The
alternative, one transaction each plus an outbox to make the fan-out reliable, was a
substantially larger design for no invariant gained.

### One transaction spanning two bounded contexts

The guideline above covers instances of the same aggregate type inside one context. The
following governs a driver in one context calling another context's inport synchronously
(§ 11 rule 3), so that two aggregates in different contexts share **one** transaction.

**It has no subject today** — there is one bounded context — and it is kept in full because it
is the rule that will be needed on the day a second one is added, which is also the day nobody
will want to stop and derive it.

**This is permitted, narrowly.** All of the following must hold:

1. The call is driver-to-inport, per § 11 rule 3. No other layer may open a cross-context
   transaction.
2. The caller genuinely needs confirmation before it can commit its own decision. The worked
   example was a cancellation: a parent reported cancelled while its dependents still believe
   it is going ahead is the failure such a use case exists to prevent. "It would be
   convenient" does not qualify.
3. The callee joins the caller's transaction (`REQUIRED`) rather than opening its own. A
   `REQUIRES_NEW` callee would give the illusion of atomicity while committing
   independently — worse than not sharing at all, because the divergence would be silent.
4. No invariant spans the two aggregates. They are updated together for consistency of
   *outcome*, not because either enforces a rule about the other. This keeps § 10's actual
   prohibition intact.

**Where it is not permitted:** a notification. If the receiving context may react whenever it
likes, use a domain event and `AFTER_COMMIT` + `REQUIRES_NEW`. Reaching for a shared
transaction there buys coupling and lock duration for nothing.

The cost is real: the transaction is open for the duration of N cross-context calls, both
contexts fail together, and neither can be deployed separately without revisiting this. That is
the trade for never having a cancelled parent with live dependents.

Switching an interaction between the two models — shared transaction ↔ event — is a
cross-context interaction model change and an ADR trigger (`sdd.playbook.md` § 6 item 10).
Establishing the *first* such transaction was reviewed as trigger 5 ("modifying transaction
boundaries") and ruled **no ADR** by the maintainer, on the grounds that ADR-0008 had
already examined this exact interaction and been rejected in favour of the direct call.
This section records that ruling so the precedent is a rule rather than a decision buried
in one use-case spec.

Found by `ddd-hex-reviewer` under `Undocumented` during the UC09 review, which correctly
noted that whether trigger 5 fires was "a matter of opinion" while this was unwritten.

If a genuine cross-aggregate invariant ever appears, that is not a transaction-scoping
question but a modelling error: the aggregate boundary is wrong. Raise it rather than
widening the transaction.

## 11. Registered Bounded Contexts

This table is the **authoritative registry** of top-level packages under the service's
root package. It exists so that context boundaries are a checkable fact rather than a
matter of opinion.

**It is parsed.** `ContextRegistryTest` reads this table and diffs it against the
top-level packages on disk, so the two cannot disagree silently. That means registering a
context **starts here**, not with a `mkdir`: create the package first and the test fails
with an unregistered package; add the row first and it fails with a registered package
that does not exist. Either way the gap is named.

**Format contract — the test depends on this shape, so do not restyle it.**

- The first markdown table after this heading is the registry.
- Column 1 is the package name in backticks, and it is a single path segment.
- Column 2 is the kind: exactly `Bounded Context`, `Shared Kernel` or `Composition Root`.
- One row per top-level package. No row may be added without an ADR (see Rules below).

| Package | Kind | Owns | ADR |
|---------|------|------|-----|
| `contract` | Bounded Context | The `Master` aggregate root and the `Contract` entities inside it. One repository outport, `MasterRepository`; no `ContractRepository`. | `adr/0024-one-context-with-master-as-the-aggregate-root.adr.md` |
| `shared` | Shared Kernel | Cross-context building blocks only (`DomainEvent`, `ClockPort`, `DomainEventPublisher`). Not a context. See § 9. | — |
| `bootstrap` | Composition Root | Wiring only. Not a context. See § 4.9. | — |

The service's entry point deliberately sits in the **root** package rather than in
`bootstrap`, so it is not a row here. Two things depend on that position: Spring's
component scan defaults to it, and `ArchitectureRoot` derives the package root from it
instead of repeating a string literal in three tests.

### Rules

1. **Adding a row requires an ADR.** A new bounded context is an ADR trigger
   (`sdd.playbook.md` § 6, item 9). `adr/0003-separate-guide-bounded-context.adr.md`
   is the precedent: it states the context boundary, the integration pattern, and
   what moved to `shared`.
2. **A new top-level package that is not in this table is drift**, regardless of
   how reasonable it looks. The reviewer reports it as `DRIFT` and the increment
   is blocked until either the package is removed or the ADR exists and this
   table is updated.
3. **Contexts do not import each other's internals.** A context's `core.inport` triple is
   its **published API** — § 4.2 calls it "the application boundary" — and one context MAY
   depend on another's inport. Everything else is closed: no import of another context's
   `core.domain`, `core.outport`, `inbound.*` or `outbound.*`, in either direction.

   Cross-context communication therefore has three sanctioned forms:

   | Form | When | Example |
   |------|------|---------|
   | Domain event via `shared.domain.event` | The publisher does not need to know the outcome | *no current example — one context* |
   | Synchronous call to the other context's **inport**, from the orchestrating driver | The caller's own outcome depends on the callee's | UC12 → UC09 |
   | The caller's own outport | The dependency is on something outside this system | `AvailabilityChecker` |

   **Why the inport and not an outport.** The second form is ordinary orchestration:
   § 4.4 makes a driver responsible for coordinating a use case, and calling a published
   API is what coordination looks like. Declaring an outport for it would add an interface
   whose only implementation is a single known adapter delegating to that same inport —
   indirection that changes the import graph without reducing the coupling, since the
   caller still depends on the callee's behaviour, contract and availability.

   **This narrows ADR-0003.** That ADR asserted "both contexts depend on `shared.domain`
   only", which was true when `guide` was extracted and is no longer the rule. ADR-0003 is
   immutable and stands as the record of the extraction; this table is the current rule.
   The coupling it permits is deliberate and bounded — a published API, never an internal.
4. **`shared` must not depend on any bounded context** (§ 9). Adding a
   context-specific type to `shared` is drift even though `shared` is registered.
5. Every context follows the same internal ontology (§ 3): `core` /
   `inbound` / `outbound`. A context that invents its own internal layout is
   drift.

### Growing the registry

The pressure to add a context usually means one of three things. Only the third
justifies a new row:

- A new **use case** on an existing aggregate → belongs in the existing context.
- A new **read model / projection** → `outbound.persistence.read` in the owning
  context (§ 4.6), not a new context.
- A genuinely different **ubiquitous language** with its own invariants and
  lifecycle, where sharing the model would force one aggregate to serve two
  meanings → new context, with an ADR making that argument explicitly.
