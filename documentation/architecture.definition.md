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
com.dominikgaller.alpinebooking              ← shared root
├── bootstrap                                ← composition root (cross-context)
│   ├── AlpineBookingApplication
│   └── <ContextName>Config
├── shared                                   ← shared kernel (cross-context building blocks)
│   ├── domain                               ← see § 9
│   │   └── event
│   │       └── DomainEvent                  ← marker interface for all domain events
│   ├── outport                              ← ports needed by more than one context
│   └── outbound                             ← adapters implementing shared.outport
└── <bounded-context>                        ← one sub-package per bounded context (e.g. booking)
    ├── core
    │   ├── domain
    │   │   └── <aggregate>                  ← one sub-package per aggregate root (e.g. tourbooking)
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

A write-side query MAY filter on a domain criterion — `findConfirmedByTourId` rather than
`findByTourId` plus a filter in the caller. Three conditions:

1. **The aggregate still enforces the rule.** The query *selects candidates*; it does not
   replace the guard. `markActive` must reject a non-CONFIRMED booking whether or not the
   query already excluded it. A query that becomes the only enforcement is drift.
2. **The criterion is named in the method.** `findConfirmedByTourId`, not
   `findByTourIdAndStatus(status)` with the constant supplied by the caller — that just
   relocates the knowledge to the caller.
3. **It is specified.** The port spec states the criterion, so the duplication between SQL
   and aggregate guard is deliberate and visible rather than discovered later.

Why permit the duplication at all: the alternative is a status filter in the caller, and
for an inbound adapter that is business logic in the wrong layer (§ 4.8). It can also be
load-bearing — `TourStartedListener` fans out across bookings in one transaction, and
`markActive` throws for a non-activatable one, so filtering in the caller is what stops a
single ineligible booking rolling back the batch.

The duplication is the accepted cost. If the criterion changes, both places change, and the
port spec is where that is recorded.

> Previously this rule existed only as an argument inside
> `ports/tour-booking-repository.outport.spec.md` § 2.4 — an authority-level-16 document,
> which made the precedent unappealable and unenforceable. Raised to definition level after
> `ddd-hex-reviewer` reported it as the last remaining undocumented rule.

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

### 4.9 `bootstrap`

Composition root and wiring. Lives at the **shared root level** (`com.dominikgaller.alpinebooking.bootstrap`),
**outside any bounded-context package**. This reflects that bootstrap is not aligned to any single context —
it wires the whole application.

Contains:
- Spring Boot main class (`AlpineBookingApplication`)
- Per-context configuration classes (e.g. `BookingConfig`)
- Bean wiring

Rules:

- This package may depend on everything else.
- No business logic.
- No domain logic.
- Only wiring and configuration.
- The main class scans `com.dominikgaller.alpinebooking` (the shared root) to discover all bounded contexts automatically.

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
HTTP client with nothing bounding it — a cancellation could be dated before the booking
existed, or years in the future.

**A driver behind a REST endpoint MUST read the timestamp from `ClockPort`. It MUST NOT
accept one from the request.** The time an action happened is the system's observation,
not the caller's claim, and there is no business case in this project for a client
asserting it. Nothing needs validating, because nothing is accepted.

**A driver receiving a timestamp from another bounded context MUST use the one supplied,
falling back to `ClockPort` only when it is absent.** UC06 and UC07 take `startedAt` and
`completedAt` from `TourStarted` / `TourCompleted`; UC09 takes `cancelledAt` from the
guide side. These are facts already recorded in the originating context, crossing a
boundary. Re-dating them with the receiver's clock would make a booking claim it completed
at a different moment than the tour did — the timestamps would drift apart by the event's
delivery latency, and neither would be wrong-looking on its own.

The discriminator is the **caller**, not the field:

| Inbound adapter | Timestamp source |
|-----------------|------------------|
| `inbound.rest` → driver | `ClockPort` only. No timestamp on the request DTO |
| `inbound.listener` → driver | the event's timestamp, `ClockPort` as fallback |
| another context's driver → inport | the caller's timestamp, `ClockPort` as fallback |

A command reached from both surfaces therefore carries a nullable timestamp, and the REST
adapter simply never populates it.

Recorded after `ddd-hex-reviewer` reported the gap under `Undocumented` during the UC08
review: `architecture.definition.md` § 8 permitted "provide time from application
service" without saying whether an inbound adapter may override the clock, so three use
cases had quietly answered yes.

## 9. Shared Kernel (`shared`)

Cross-cutting building blocks that are not owned by any single bounded context.

```
shared
├── domain
│   ├── TourId                    ← identity owned by no context (ADR 0005 category 3)
│   └── event
│       ├── DomainEvent           ← marker interface
│       └── <CrossContextEvent>   ← e.g. TourStarted
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
  publishing one (e.g. `TourStarted`; see § 11 rule 3 and ADR 0002).
- Identities owned by no context in this system (`TourId`; ADR 0005 category 3).
- Framework-free; no Spring, no IO.

### `shared.outport`

Ports the core of **more than one** context needs, where neither context owns the
abstraction — `ClockPort`, `DomainEventPublisher`.

A port used by exactly one context belongs in that context's `core.outport`, not here.

### `shared.outbound`

**Adapters implementing `shared.outport` live here, not inside a bounded context.**

Rationale: an implementation of a shared port is not owned by any context either. Putting
`ClockPort`'s adapter in `booking.outbound.integration` forces `guide` to obtain a clock
from `BookingConfig`, which makes `guide` depend on `booking`'s wiring for something
neither context owns. That is a dependency the context split exists to prevent, and it
made ADR 0003's claim that "both contexts depend on `shared.domain` only" false.

Wiring: `bootstrap.SharedConfig` declares the shared beans. Per-context configs
(`BookingConfig`, `GuideConfig`) declare only their own context's adapters.

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
- Do not add context-specific types here (e.g., `TourBookingRequested` stays in
  `booking.core.domain.tourbooking.event`).
- `shared` must not depend on any bounded context. Any context may depend on `shared`.
- The test is **ownership, not usage**. "Both contexts use it" is not sufficient — if one
  context owns it, the other goes through an event or its own port.
- A context MUST NOT wire another context's beans. If `guide` needs a shared adapter, it
  comes from `SharedConfig`, never from `BookingConfig`.

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

Where the guideline is deliberately not followed: `TourStartedListener` activates every
CONFIRMED booking for a tour in a single `REQUIRES_NEW` transaction (UC06). Accepted
because no invariant spans the bookings — each `markActive` is independent, and the
listener is simply a fan-out. The alternative (one transaction per booking, plus an
outbox to make the fan-out reliable) is a substantially larger design for no invariant
gained.

### One transaction spanning two bounded contexts

The guideline above covers instances of the same aggregate type inside one context. UC12
does something the document did not previously address: a `GuideTour` mutation and N
`TourBooking` mutations share **one** transaction, across a context boundary, because the
guide's driver calls `booking`'s inport synchronously (§ 11 rule 3).

**This is permitted, narrowly.** All of the following must hold:

1. The call is driver-to-inport, per § 11 rule 3. No other layer may open a cross-context
   transaction.
2. The caller genuinely needs confirmation before it can commit its own decision. UC12
   qualifies: a tour reported cancelled while its bookings still believe it is going ahead
   is the failure the use case exists to prevent. "It would be convenient" does not qualify.
3. The callee joins the caller's transaction (`REQUIRED`) rather than opening its own. A
   `REQUIRES_NEW` callee would give the illusion of atomicity while committing
   independently — worse than not sharing at all, because the divergence would be silent.
4. No invariant spans the two aggregates. The tour and its bookings are updated together
   for consistency of *outcome*, not because either enforces a rule about the other. This
   keeps § 10's actual prohibition intact.

**Where it is not permitted:** a notification. If the receiving context may react whenever
it likes, use a domain event and `AFTER_COMMIT` + `REQUIRES_NEW`, as UC06 and UC07 do.
Reaching for a shared transaction there buys coupling and lock duration for nothing.

The cost is real and accepted: the transaction is open for the duration of N cross-context
calls, both contexts fail together, and neither can be deployed separately without
revisiting this. That is the trade for never having a cancelled tour with live bookings.

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

This table is the **authoritative registry** of top-level packages under
`com.dominikgaller.alpinebooking`. It exists so that context boundaries are a
checkable fact rather than a matter of opinion: `ddd-hex-reviewer` enumerates the
top-level packages on disk and diffs them against this table on every increment.

| Package | Kind | Owns | ADR |
|---------|------|------|-----|
| `booking` | Bounded Context | `TourBooking` aggregate — request, confirm, cancel, change participants, activate, complete | — (original context) |
| `guide` | Bounded Context | `GuideTour` aggregate — guide-side tour lifecycle (start, complete, cancel-by-guide) | `adr/0003-separate-guide-bounded-context.adr.md` |
| `shared` | Shared Kernel | Cross-context building blocks only (`TourId`, `DomainEvent`, cross-context events, `ClockPort`, `DomainEventPublisher`). Not a context. See § 9. | `adr/0003-…` (`TourId` extraction) |
| `bootstrap` | Composition Root | Wiring only. Not a context. See § 4.9. | — |

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
   | Domain event via `shared.domain.event` | The publisher does not need to know the outcome | UC05 → UC06 (`TourStarted`) |
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
