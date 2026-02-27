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
root-package (e.g. com.alpine.booking.booking, where booking (the last one) represents a bounded context
├── core
│   ├── domain
│   ├── inport
│   └── outport
├── inbound
│   ├── driver
│   └── rest
├── outbound
│   ├── persistence
│   │   ├── write
│   │   └── read
│   └── integration
├── listeners
└── bootstrap
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

Defines the **application boundary** as interfaces:

- Use case interfaces (commands)
- Query interfaces (read-only) – only if you want queries to be part of the core boundary

Rules:

- Framework-free.
- Must not reference adapters (no REST DTOs, no jOOQ records).
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

Contains delivery concerns only:

- Controllers
- REST request/response DTOs
- Mapping between HTTP and inports

Rules:

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

### 4.8 `listeners`

Technical listeners/hooks that react to infrastructure events (framework-driven).

Rules:

- Must be treated as adapters.
- Must not contain business logic; forward into inports or outports as appropriate.
- If a listener triggers business behaviour, it should call an inport (not a concrete use case class).

### 4.9 `bootstrap`

Composition root and wiring:

- Spring Boot main class
- Configuration
- Bean wiring

Rules:

- This package may depend on everything else.
- No business logic.
- No domain logic.
- Only wiring and configuration.

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
3. `inbound.rest` depends only on `core.inport` (and its own DTOs).
4. `inbound.driver` depends on `core.domain`, `core.inport`, `core.outport` only.
5. `outbound.*` packages implement `core.outport` and must not be referenced as concrete types from drivers/controllers.
6. Persistence types must not cross boundaries into the core (no jOOQ/JPA types in ports).
7. Only `bootstrap` wires implementations; it contains no business logic.

## 7. Practical “Where does this go?” Cheatsheet

- New Aggregate / VO / Domain rule → `core.domain`
- New Use Case interface → `core.inport`
- New Use Case implementation (orchestration) → `inbound.driver`
- New Repository interface / external dependency abstraction → `core.outport`
- New REST endpoint + DTOs → `inbound.rest`
- New DB mapping for aggregates (write) → `outbound.persistence.write`
- New query/projection (read) → `outbound.persistence.read`
- New external API client / publisher → `outbound.integration`
- New wiring/config → `bootstrap`

## 8. Time, randomness, and “now”
- Never call Instant.now() in the domain.
- Use an outbound port ClockPort or provide time from application service.

## 9. Anti-patterns (explicitly forbidden)
- JPA annotations in domain objects.
- Calling repositories from aggregates/entities/value objects.
- Using primitives for domain concepts when they carry meaning (money, ids, email, etc.).
- Cross-aggregate invariants enforced inside one transaction “because it’s convenient”.
