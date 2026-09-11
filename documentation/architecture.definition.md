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

### 2.1 The two technologies this style is most at risk from

Two choices in `technical.spec.md` put continuous pressure on the boundary, and
naming them here is cheaper than rediscovering them:

**JPA.** An ORM's entire value proposition is that your domain classes *are* your
rows. Accepting that offer would put `@Entity`, `@Id` and lazy-loading proxies
inside the aggregate and end the framework-free claim in one commit. So this
project keeps two separate class hierarchies — the aggregate and the
`*Entity` — with an explicit mapper between them, and pays the mapping cost
deliberately. See § 4.6 and `adr/0001-technical-stack.adr.md`.

**GraphQL.** A GraphQL schema is a graph, and the obvious way to resolve a field
is to reach for whatever object holds it — which invites resolvers that walk from
one aggregate into another, and `@SchemaMapping` methods that quietly become
business logic. So resolvers map to inports and nothing else, and a field that
cannot be answered from one use case's result is a missing use case rather than a
missing traversal. See § 4.5 and `adr/0008-graphql-only-inbound-adapter.adr.md`.

## 3. Package Structure

```
com.jobradleasing.contractmanagement          ← shared root
├── bootstrap                                 ← composition root (cross-context)
│   ├── ContractManagementApplication
│   └── <ContextName>Config
├── shared                                    ← shared kernel (cross-context building blocks)
│   ├── domain                                ← see § 9
│   │   └── event
│   │       └── DomainEvent                   ← marker interface for all domain events
│   ├── outport                               ← ports needed by more than one context
│   └── outbound                              ← adapters implementing shared.outport
└── <bounded-context>                         ← one sub-package per bounded context
    ├── core
    │   ├── domain
    │   │   └── <aggregate>                   ← one sub-package per aggregate root
    │   │       ├── event                     ← domain events for this aggregate
    │   │       └── exception                 ← domain exceptions for this aggregate
    │   ├── inport
    │   │   ├── command                       ← input data carriers (data classes)
    │   │   ├── result                        ← output data carriers (data classes)
    │   │   └── usecase                       ← use case interfaces (inbound ports)
    │   └── outport
    ├── inbound
    │   ├── driver
    │   ├── listener                          ← Spring @TransactionalEventListener / @EventListener hooks
    │   └── graphql
    │       ├── input                         ← inbound GraphQL argument types
    │       └── payload                       ← outbound GraphQL result types
    └── outbound
        ├── persistence
        │   ├── write
        │   └── read
        └── integration
```

The GraphQL **schema** is not in this tree. It is a resource:
`src/main/resources/graphql/<context>/<name>.graphqls`. That is a consequence of
schema-first GraphQL and is discussed in § 4.5.

## 4. Responsibilities by Package

### 4.1 `core.domain`

Contains the pure domain model:

- Aggregates, Entities, Value Objects
- Domain Services (pure business logic only)
- Domain Events (types only)

Rules:

- MUST NOT depend on any other project package.
- MUST NOT use Spring / Jakarta / Jackson / GraphQL / persistence frameworks.
  **`jakarta.persistence` is the one to watch**: a single `@Entity` here ends the
  framework-free claim, and the Kotlin JPA plugin would silently generate a no-arg
  constructor for it, defeating the Always-Valid guarantee without a compile error.
- MUST NOT perform IO, remote calls, DB access, or message publishing.
- MUST NOT be `open` for the benefit of a framework proxy. If something needs to be
  subclassed for infrastructure reasons, the infrastructure is in the wrong place.

### 4.2 `core.inport`

Defines the **application boundary** as interfaces and data carriers. Split into three sub-packages:

| Sub-package | Contents | Naming convention |
|-------------|----------|-------------------|
| `inport.command` | Input data carriers (immutable data classes) | `*Command` |
| `inport.result` | Output data carriers (immutable data classes) | `*Result` |
| `inport.usecase` | Use case interfaces (inbound ports) | `*UseCase` |

Rules:

- Framework-free.
- Must not reference adapters (no GraphQL input types, no JPA entities).
- `usecase` interfaces may only reference types from `inport.command`, `inport.result`,
  and `core.domain.<aggregate>.exception`.
- Inport types are stable contracts: keep them small and intention-revealing.

### 4.3 `core.outport`

Defines what the core needs from the outside world as interfaces:

- Repositories (write-side)
- External gateways (e.g., employer directory, bike catalogue, pricing)
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

- `@Controller`, `@RestController`, `@QueryMapping`, `@MutationMapping`, `@SchemaMapping`
- `@KafkaListener`, `@RabbitListener`
- `@Scheduled`
- Direct usage of concrete adapter classes (`outbound.*` classes)

Dependency rules:

- MAY depend on: `core.domain`, `core.inport`, `core.outport`
- MUST NOT depend on: `inbound.graphql`, `outbound.*` implementations, other adapters

### 4.5 `inbound.graphql`

Contains delivery concerns only.

**This package has a different shape from the REST equivalent it replaces, and the
difference is not cosmetic.** In a REST adapter the HTTP contract — routes, verbs,
status codes — exists only in annotations, so it has to be collected onto a
dedicated `*RestAPI` interface to be readable in one place. GraphQL is schema-first:
the contract is already in one place, as a `.graphqls` file that the build loads and
validates the resolvers against. Introducing an interface to restate it would create
a second contract that can disagree with the first.

So the two-type split collapses to one, and the schema takes the missing role:

| Type | Naming | Location | Responsibility |
|------|--------|----------|----------------|
| Schema | `<name>.graphqls` | `src/main/resources/graphql/<context>/` | **The contract**: types, operations, nullability, enums |
| `*GraphQLController` | class | `inbound.graphql` | Resolver: maps input → command, delegates to inport, maps result → payload |
| `*Input` | data class | `inbound.graphql.input` | Inbound argument types |
| `*Payload` | data class | `inbound.graphql.payload` | Outbound result types |
| `*ExceptionResolver` | class | `inbound.graphql` | Maps domain exceptions to `GraphQLError` with an `ErrorType` classification |

Rules:

- All GraphQL mapping annotations (`@QueryMapping`, `@MutationMapping`,
  `@SchemaMapping`, `@Argument`) live in this package and nowhere else.
- Every operation a controller maps MUST exist in a schema file, and every operation
  in a schema file MUST have exactly one resolver. An unresolved schema field fails at
  startup; a resolver with no schema field is dead code that no test will catch.
- Controllers MUST depend only on `core.inport` (interfaces), not on implementations.
- MUST NOT expose `core.domain` types in the schema. A value object is mapped to a
  scalar or an input type; it is never serialised directly.
- MUST NOT access repositories or outbound implementations.
- **A `@SchemaMapping` that loads data is a use case, not a field resolver.** Resolving
  a nested field by reaching into another aggregate is how a graph API grows an N+1
  problem and a business rule at the same time. If a payload needs a field the use case
  did not return, widen the result or add a read-side query — do not traverse.
- Mapping responsibility:
    - `*Input` → Command
    - Result / read data → `*Payload`

#### Errors are not status codes

A GraphQL response is `200 OK` with an `errors` array. The error *kind* therefore
has to be carried explicitly, in `extensions.classification`, rather than inferred
from a status line. Specs name the classification, not a status code:

| Domain condition | `ErrorType` | The equivalent an HTTP reader expects |
|------------------|-------------|----------------------------------------|
| Invariant violated by caller input | `BAD_REQUEST` | 400 |
| Aggregate not found | `NOT_FOUND` | 404 |
| Illegal state transition | `BAD_REQUEST` with `classification: CONFLICT` | 409 |
| Outbound dependency unavailable | `INTERNAL_ERROR` | 502 |

Spring GraphQL's `ErrorType` enum has no `CONFLICT` member, which is why the third
row is spelled out: the resolver sets a custom `classification` extension so an
illegal transition stays distinguishable from a malformed argument. Collapsing both
into `BAD_REQUEST` would make the two indistinguishable to a client, and they need
different handling — one is retryable after a state change, the other never is.

### 4.6 `outbound.persistence`

Contains DB implementations and data access.

Important: we split **read** and **write** for clarity of intent and to keep the mental model consistent in SDD.

#### The aggregate and the entity are two different classes

This is the load-bearing rule of the package, and the one JPA will erode if it is
not stated plainly.

- `MasterLeasingContract` is the aggregate. It lives in `core.domain`, is
  framework-free, enforces invariants and has no no-arg constructor.
- `MasterLeasingContractEntity` is the row. It lives here, carries `@Entity`,
  `@Id` and `@Column`, has mutable properties and a no-arg constructor the Kotlin
  JPA plugin generates, and enforces nothing.
- `MasterLeasingContractMapper` converts between them, in both directions.

The obvious objection is that this is duplication, and it is: every field appears
in three places. The objection is correct and the cost is accepted, because the
alternative costs more. An `@Entity` aggregate:

1. **cannot be Always-Valid.** JPA requires a no-arg constructor and mutable
   properties, so the object can exist half-built, and Hibernate will build it that
   way during reconstitution regardless of what the constructors say.
2. **leaks its lifecycle.** A lazily-initialised collection throws outside a
   session, so the aggregate's behaviour depends on whether a transaction is open —
   a property `core.domain` must not have.
3. **makes the schema the model.** Changing a column becomes a domain change and
   changing the domain becomes a migration, which is exactly the coupling hexagonal
   architecture exists to prevent.

`ddl-auto=validate` and Flyway-owned DDL (`technical.spec.md`) are the other half
of this: Hibernate never generates schema, it only checks that the schema Flyway
produced matches the mappings.

#### `outbound.persistence.write`

Write-side persistence:

- Implements repository outports used by commands/use cases
- Loads aggregates and persists changes
- Responsible for mapping between DB representation and domain model

Rules:

- MAY depend on: `core.domain`, `core.outport`
- MUST NOT depend on: `inbound.*`
- MUST NOT expose persistence types into the core (no JPA entity, no
  `org.springframework.data.*` type, in any port signature)
- MUST NOT return a managed entity from a repository method. The aggregate handed back
  is a detached, mapped object; if the caller's mutations reached the database without
  an explicit `update`, the transaction boundary would be a fiction.

Typical characteristics:

- Transaction-aware (but transaction boundary is owned by driver/use case)
- Focused on **consistency and invariants** (aggregate boundaries)

##### Selection criteria in write-side queries

A write-side query MAY filter on a domain criterion — `findActiveByMasterContractId`
rather than `findByMasterContractId` plus a filter in the caller. Three conditions:

1. **The aggregate still enforces the rule.** The query *selects candidates*; it does not
   replace the guard. `activate` must reject a non-`PENDING_ACTIVATION` contract whether
   or not the query already excluded it. A query that becomes the only enforcement is drift.
2. **The criterion is named in the method.** `findTerminableByMasterContractId`, not
   `findByMasterContractIdAndStatus(status)` with the constant supplied by the caller —
   that just relocates the knowledge to the caller.
3. **It is specified.** The port spec states the criterion, so the duplication between the
   query and the aggregate guard is deliberate and visible rather than discovered later.

Why permit the duplication at all: the alternative is a status filter in the caller, and
for an inbound adapter that is business logic in the wrong layer (§ 4.8). It can also be
load-bearing — `MasterLeasingContractActivatedListener` fans out across individual
contracts in one transaction, and `activate` throws for a non-activatable one, so
filtering in the caller is what stops a single ineligible contract rolling back the batch.

The duplication is the accepted cost. If the criterion changes, both places change, and the
port spec is where that is recorded.

#### `outbound.persistence.read`

Read-side persistence:

- Implements **read-only** access patterns optimized for queries (projections, joins, denormalized views)
- Does **not** load aggregates to answer queries
- Can return read models / projection DTOs

What is “read” here?

- Any access that is **side-effect free** and does not require domain invariants to be enforced.
- Queries best served by a projection than by aggregate reconstruction.

What belongs here:

- “List / search / overview” queries
- “Detail view” queries that join multiple tables
- Analytics-style or reporting queries (within reason)

Rules:

- MAY depend on: `core.outport` (if query ports exist), and optionally shared identifiers/value types from `core.domain` (e.g., IDs)
- MUST NOT call write repositories.
- MUST NOT perform domain mutation.
- If it returns DTOs, those DTOs MUST NOT be used as GraphQL payload types without explicit mapping in `inbound.graphql` (to keep the schema stable).

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
the Spring `ApplicationEventPublisher` rather than a GraphQL request or external message broker.

For future external message consumers (Kafka, RabbitMQ), use `inbound.consumer` or `inbound.messaging`.

Rules:

- Must not contain business logic; forward into inports or outports as appropriate.
- If a listener triggers business behaviour, it should call an inport (not a concrete use case class).
- Must not access `outbound.*` implementations directly.

### 4.9 `bootstrap`

Composition root and wiring. Lives at the **shared root level**
(`com.jobradleasing.contractmanagement.bootstrap`), **outside any bounded-context
package**. This reflects that bootstrap is not aligned to any single context — it
wires the whole application.

Contains:
- Spring Boot main class (`ContractManagementApplication`)
- Per-context configuration classes (e.g. `MasterLeasingConfig`)
- Bean wiring

Rules:

- This package may depend on everything else.
- No business logic.
- No domain logic.
- Only wiring and configuration.
- The main class scans `com.jobradleasing.contractmanagement` (the shared root) to discover all bounded contexts automatically.

## 5. Command vs. Query in SDD Terms

In SDD we need a stable mapping from “spec intent” to implementation.

Commands (state change) — a GraphQL `mutation`:

- Spec talks about: “When X happens, system must…”
- Implementation lives in: `inbound.driver` (use case) + `core.domain` (business rules) + `outbound.persistence.write` (repo)

Queries (read-only) — a GraphQL `query`:

- Spec talks about: “System shows/list/returns …”
- Implementation lives in: `inbound.graphql` (resolver/mapping) + `outbound.persistence.read` (projection)
  and optionally `core.inport`/`core.outport` if you model query ports

Guiding rule:

- If the behaviour must respect domain invariants via aggregate logic → treat it as command-side (load aggregate).
- If it is purely informational and best served as projection → treat it as read-side (no aggregate load).

**The GraphQL operation type is not the discriminator.** A `query` that has to load an
aggregate because a caller depends on invariant-checked state is still command-side in
this taxonomy, and belongs behind an inport. `ReadLeasingTermsUseCase` (UC09) is exactly
that case: it is read-only, but it is a published inport rather than a projection,
because the *other context* depends on the answer being authoritative. Convenience of
the transport does not decide where logic lives.

## 6. Dependency Rules Summary (Enforceable)

1. `core.domain` depends on nothing else.
2. `core.inport` and `core.outport` are framework-free.
3. `inbound.graphql` depends only on `core.inport`, its own input/payload types, and
   `core.domain.<aggregate>.exception` — the last solely so `*ExceptionResolver` can
   map domain exceptions to error classifications, which § 4.5 assigns it as its job.
   It must not depend on `core.outport`, on `inbound.driver`, or on any `outbound.*`
   implementation. The § 4.5 prohibition on `core.domain` is about **the schema**: no
   domain object appears in a GraphQL type.
4. `inbound.driver` depends on `core.domain`, `core.inport`, `core.outport` only.
5. `outbound.*` packages implement `core.outport` and must not be referenced as concrete types from drivers/controllers.
6. Persistence types must not cross boundaries into the core (no JPA entity, no
   `jakarta.persistence` or `org.springframework.data` type in any port).
7. Only `bootstrap` wires implementations; it contains no business logic.

## 7. Practical “Where does this go?” Cheatsheet

- New Aggregate / VO / Domain rule → `core.domain`
- New Use Case interface → `core.inport.usecase`
- New Command / Result type → `core.inport.command` / `core.inport.result`
- New Use Case implementation (orchestration) → `inbound.driver`
- New Repository interface / external dependency abstraction → `core.outport`
- New GraphQL type or operation → `src/main/resources/graphql/<context>/*.graphqls`
- New GraphQL resolver (delegation) → `inbound.graphql` `*GraphQLController`
- New DB mapping for aggregates (write) → `outbound.persistence.write` (entity + mapper + repository)
- New query/projection (read) → `outbound.persistence.read`
- New external API client / publisher → `outbound.integration`
- New wiring/config → `bootstrap`

## 8. Time, randomness, and “now”
- Never call `Instant.now()` in the domain.
- Use an outbound port `ClockPort` or provide time from the application service.

This matters more here than in most domains, because almost every field in this one
is a date that another date is computed from: `activation_date`, `term_start`,
`term_end`, `first_due_date`, `cancelled_date`, the notice period, the early-claim
window. A test that cannot fix "now" cannot assert any of them.

### 8.1 Who may supply a domain timestamp

The rule above says where `now()` may be read. It does not say who is allowed to
*decide* what "now" was, and that gap is worth closing before it is discovered.

**A driver behind a GraphQL mutation MUST read the timestamp from `ClockPort`. It
MUST NOT accept one from the request.** The time an action happened is the system's
observation, not the caller's claim, and there is no business case in this project
for a client asserting it. Nothing needs validating, because nothing is accepted.

**A driver receiving a timestamp from another bounded context MUST use the one
supplied, falling back to `ClockPort` only when it is absent.** UC06 takes
`activatedAt` from `MasterLeasingContractActivated`; UC08 takes `terminatedAt` from
the master-contract side. These are facts already recorded in the originating
context, crossing a boundary. Re-dating them with the receiver's clock would make an
individual contract claim it was terminated at a different moment than its master
contract was cancelled — the timestamps would drift apart by the event's delivery
latency, and neither would be wrong-looking on its own.

The discriminator is the **caller**, not the field:

| Inbound adapter | Timestamp source |
|-----------------|------------------|
| `inbound.graphql` → driver | `ClockPort` only. No timestamp on the input type |
| `inbound.listener` → driver | the event's timestamp, `ClockPort` as fallback |
| another context's driver → inport | the caller's timestamp, `ClockPort` as fallback |

A command reached from both surfaces therefore carries a nullable timestamp, and the
GraphQL adapter simply never populates it.

**Business dates are not timestamps and this rule does not touch them.** `termStart`
on an individual leasing contract is a negotiated commercial term supplied by the
caller, not an observation of when anything happened. It is validated as a business
value — against the master contract's window, against `termEnd` — and the clock has
no opinion about it. The test is whether the field records *when the system observed
something* (clock) or *what the parties agreed* (input).

## 9. Shared Kernel (`shared`)

Cross-cutting building blocks that are not owned by any single bounded context.

```
shared
├── domain
│   ├── EmployerId                ← identity owned by no context (ADR 0005 category 3)
│   ├── LessorId                  ← likewise
│   ├── Money                     ← value type both contexts denominate amounts in
│   └── event
│       ├── DomainEvent           ← marker interface
│       └── <CrossContextEvent>   ← e.g. MasterLeasingContractActivated
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
  publishing one (e.g. `MasterLeasingContractActivated`; see § 11 rule 3 and ADR 0002).
- Identities owned by no context in this system (`EmployerId`, `LessorId`; ADR 0005
  category 3).
- `Money` — not an identity, and worth stating why it qualifies anyway: both
  contexts denominate amounts, the invariants are identical (scale, non-negative
  where required, currency agreement on arithmetic), and neither context owns the
  concept. A `Money` per context would be two copies of the same rounding rule, and
  rounding rules that drift are the classic way a leasing system starts disagreeing
  with itself by cents.
- Framework-free; no Spring, no IO.

**Not everything both contexts mention belongs here.** `CancellationReason` is
declared separately in each context and deliberately so: the master contract's
*Kündigungsgrund* is a lessor's ground for terminating a framework agreement, and
the individual contract's is an employee's reason for ending a lease. They are the
same word for two business facts with different permitted values. Sharing the type
would force one context's vocabulary onto the other — the failure § 9's ownership
test exists to prevent.

### `shared.outport`

Ports the core of **more than one** context needs, where neither context owns the
abstraction — `ClockPort`, `DomainEventPublisher`.

A port used by exactly one context belongs in that context's `core.outport`, not here.

### `shared.outbound`

**Adapters implementing `shared.outport` live here, not inside a bounded context.**

Rationale: an implementation of a shared port is not owned by any context either.
Putting `ClockPort`'s adapter in `masterleasing.outbound.integration` forces
`individualleasing` to obtain a clock from `MasterLeasingConfig`, which makes one
context depend on another's wiring for something neither owns. That is a dependency
the context split exists to prevent, and it is invisible to any import check — which
is what makes it worth a rule rather than a code review.

Wiring: `bootstrap.SharedConfig` declares the shared beans. Per-context configs
(`MasterLeasingConfig`, `IndividualLeasingConfig`) declare only their own context's
adapters.

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
- Do not add context-specific types here (e.g., `MlcConfiguration` stays in
  `masterleasing.core.domain.masterleasingcontract`).
- `shared` must not depend on any bounded context. Any context may depend on `shared`.
- The test is **ownership, not usage**. "Both contexts use it" is not sufficient — if one
  context owns it, the other goes through an event or its own port.
- A context MUST NOT wire another context's beans. If `individualleasing` needs a shared
  adapter, it comes from `SharedConfig`, never from `MasterLeasingConfig`.

## 10. Anti-patterns (explicitly forbidden)
- JPA annotations in domain objects. (§ 4.6 — the one this stack makes easiest to break.)
- Calling repositories from aggregates/entities/value objects.
- Using primitives for domain concepts when they carry meaning (money, ids, percentages,
  terms, etc.).
  **Scoped for identities:** this applies to identities the context *owns*. A reference to
  an identity owned by another bounded context MAY be a plain `String` — see
  `modelling.definition.md` § Identity and `adr/0005-bounded-context-identity-boundaries.adr.md`.
- **`BigDecimal` or `Double` as a monetary amount.** `Double` for money is a defect, not
  a style choice: leasing rates are multiplied by a factor and a term in months, and
  binary floating point makes the result wrong by cents that then compound. Amounts are
  `Money`; rates and quotas are `Percentage`.
- Cross-aggregate invariants enforced inside one transaction “because it’s convenient”.
- Introducing a top-level package that is not registered in § 11.

### Multiple aggregates in one transaction — guideline, not rule

Updating several instances of the **same** aggregate type in one transaction is
**permitted**. The forbidden thing above is narrower: enforcing an *invariant that spans
aggregates* inside one transaction, which couples their consistency boundaries.

Guideline: prefer one aggregate per transaction. It bounds lock duration and keeps
failures isolated.

Where the guideline is deliberately not followed:
`MasterLeasingContractActivatedListener` activates every pending individual contract
under a master contract in a single `REQUIRES_NEW` transaction (UC06). Accepted because
no invariant spans the contracts — each `activate` is independent, and the listener is
simply a fan-out. The alternative (one transaction per contract, plus an outbox to make
the fan-out reliable) is a substantially larger design for no invariant gained.

### One transaction spanning two bounded contexts

The guideline above covers instances of the same aggregate type inside one context. UC04
does something different: a `MasterLeasingContract` mutation and N
`IndividualLeasingContract` mutations share **one** transaction, across a context
boundary, because the master-leasing driver calls `individualleasing`'s inport
synchronously (§ 11 rule 3).

**This is permitted, narrowly.** All of the following must hold:

1. The call is driver-to-inport, per § 11 rule 3. No other layer may open a cross-context
   transaction.
2. The caller genuinely needs confirmation before it can commit its own decision. UC04
   qualifies: a master contract reported cancelled while employees still hold live leases
   under it is the failure the use case exists to prevent, and it is a failure with a
   direct financial consequence — the lessor keeps invoicing for bikes on a terminated
   agreement. "It would be convenient" does not qualify.
3. The callee joins the caller's transaction (`REQUIRED`) rather than opening its own. A
   `REQUIRES_NEW` callee would give the illusion of atomicity while committing
   independently — worse than not sharing at all, because the divergence would be silent.
4. No invariant spans the two aggregates. The master contract and its leases are updated
   together for consistency of *outcome*, not because either enforces a rule about the
   other. This keeps § 10's actual prohibition intact.

**Where it is not permitted:** a notification. If the receiving context may react whenever
it likes, use a domain event and `AFTER_COMMIT` + `REQUIRES_NEW`, as UC06 does. Reaching
for a shared transaction there buys coupling and lock duration for nothing.

The two fan-outs in this system are deliberately different shapes, and the contrast is
the whole rule:

| | UC06 — activation | UC04 — cancellation |
|---|---|---|
| Mechanism | domain event, `AFTER_COMMIT`, `REQUIRES_NEW` | shared transaction, `REQUIRED` |
| If the fan-out fails | a lease stays pending; recoverable, and nobody was told otherwise | the lessor has already been told "cancelled" while leases are live |
| Cost accepted | eventual consistency | one transaction open across N cross-context calls |

The cost of the second is real and accepted: both contexts fail together, and neither can
be deployed separately without revisiting this. That is the trade for never having a
cancelled master contract with live leases under it.

Switching an interaction between the two models — shared transaction ↔ event — is a
cross-context interaction model change and an ADR trigger (`sdd.playbook.md` § 6 item 10).

If a genuine cross-aggregate invariant ever appears, that is not a transaction-scoping
question but a modelling error: the aggregate boundary is wrong. Raise it rather than
widening the transaction.

## 11. Registered Bounded Contexts

This table is the **authoritative registry** of top-level packages under
`com.jobradleasing.contractmanagement`. It exists so that context boundaries are a
checkable fact rather than a matter of opinion: `ddd-hex-reviewer` enumerates the
top-level packages on disk and diffs them against this table on every increment, and
`ContextRegistryTest` fails the build on a mismatch.

| Package | Kind | Owns | ADR |
|---------|------|------|-----|
| `masterleasing` | Bounded Context | `MasterLeasingContract` aggregate (LRV) — register, activate, amend configuration, cancel; publishes the leasing terms its leases inherit | — (original context) |
| `individualleasing` | Bounded Context | `IndividualLeasingContract` aggregate (ELV) — issue under a master contract, activate, terminate by lessee, terminate by master contract | `adr/0003-separate-individual-leasing-context.adr.md` |
| `shared` | Shared Kernel | Cross-context building blocks only (`EmployerId`, `LessorId`, `Money`, `DomainEvent`, cross-context events, `ClockPort`, `DomainEventPublisher`). Not a context. See § 9. | `adr/0003-…`, `adr/0005-…` |
| `bootstrap` | Composition Root | Wiring only. Not a context. See § 4.9. | — |

### Rules

1. **Adding a row requires an ADR.** A new bounded context is an ADR trigger
   (`sdd.playbook.md` § 6, item 9).
   `adr/0003-separate-individual-leasing-context.adr.md` is the precedent: it states
   the context boundary, the integration pattern, and what moved to `shared`.
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
   | Domain event via `shared.domain.event` | The publisher does not need to know the outcome | UC02 → UC06 (`MasterLeasingContractActivated`) |
   | Synchronous call to the other context's **inport**, from the orchestrating driver | The caller's own outcome depends on the callee's | UC05 → UC09, UC04 → UC08 |
   | The caller's own outport | The dependency is on something outside this system | a bike catalogue or employer directory |

   **Why the inport and not an outport.** The second form is ordinary orchestration:
   § 4.4 makes a driver responsible for coordinating a use case, and calling a published
   API is what coordination looks like. Declaring an outport for it would add an interface
   whose only implementation is a single known adapter delegating to that same inport —
   indirection that changes the import graph without reducing the coupling, since the
   caller still depends on the callee's behaviour, contract and availability.

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

The three unimplemented entities in the data model are worth testing against this,
because they look like candidates and mostly are not:

- **`SERVICE_AGREEMENT` (DLV)** — a separate contract with its own parties, status
  and version. This is the strongest candidate for a third context, and it is
  deliberately unbuilt rather than absorbed; see `project.definition.md`, Scope.
- **`UEV_CONTRACT` (ÜV)** — the employer-employee side of one lease. It has a
  1:1 relationship with an individual contract and no lifecycle of its own, so it
  is part of the `IndividualLeasingContract` aggregate when it is built, not a
  context.
- **`DOCUMENT`** — a file attached to either contract level. Document storage is
  infrastructure, not a ubiquitous language: `outbound.integration` behind a port.
