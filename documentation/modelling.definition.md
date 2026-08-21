# Modelling Definition

For this project, we model strictly according to the DDD building blocks.

Purpose: This handbook defines what we mean by DDD building blocks in this codebase, 
so modelling stays consistent, reviewable, and automatable.

*Core principles:*
- Model the business in the Domain layer. Infrastructure is replaceable.
- Business rules live where they are enforced (mostly aggregates/value objects).
- Prefer small aggregates with clear consistency boundaries.
- Prefer explicit types (value objects) over primitives for meaningful concepts.

# Strategic Domain Driven Design Clarifier

This is intentionally small and ontology-focused.

## System Boundary Clarification

The domain model is defined within a single bounded context.

A bounded context defines:
- The scope in which the model is consistent.
- The vocabulary used inside the model.
- The ownership of invariants.

External systems MUST NOT leak their models directly into the domain.
If necessary, mapping or anti-corruption logic MUST be used at the boundary.

# Validity & Invariants

## Always-Valid Principle

Domain objects (Entities, Aggregates, Value Objects) MUST be valid at all times.

## Definition of “valid”:

An object is considered valid if:
1.	All structural constraints are satisfied (type safety, non-null where required).
2.	All Value Object invariants hold.
3.	All state-dependent invariants hold according to the explicit state model.

“Always valid” does NOT mean “fully completed”.
Partial or evolving states are allowed if they are explicitly modelled (e.g. Pending, Confirmed, Cancelled) and define their own invariants.

## State-Dependent Invariants

Invariants MAY depend on the current lifecycle state.

Example:
- confirmedAt MAY only be set if status == CONFIRMED.
- A CancelledBooking MUST NOT allow further modifications.

State transitions MUST enforce invariants atomically.

## Policies vs Invariants

A clear distinction MUST be made between:
- Invariants → internal consistency rules of the aggregate.
- Business policies → rules depending on external systems, time, or cross-aggregate information.

Policies MUST NOT require infrastructure access inside the aggregate.
If required, external information MUST be passed as parameters or handled in Application Services.

## Rehydration Rule

Rehydrating an object from persistence MUST NOT violate invariants.

If historical data violates current invariants, this MUST be handled explicitly via:
- Data migration
- Repair use cases
- Compatibility logic

Silent acceptance of invalid domain state is NOT allowed.

# Building blocks

## Entity

*Definition:* Domain object with an identity that persists across state changes.

*Rules:*
- Has an ID (usually an ID Value Object).
- Equality is by identity, not by all fields.
- Mutable lifecycle is allowed (via behaviour methods), but invariants must hold.

*Constraints:*
- Entities are always valid.
- Entities do not perform IO.
- Entities do not depend on Spring/JPA.


## Value Object

*Definition:* Immutable object defined by its values.

*Rules:*
- Immutable (final fields; no setters).
- Equality is by value.
- Validation happens at creation time (constructor / static factory).

*Constraints:*
- Ideally, use records.
- No IO.
- No back-references to entities/aggregates.
- Prefer dedicated types to String/BigDecimal/UUID.

## Identity

*Definition:* A unique identifier for an entity or aggregate.

Entities and Aggregates MUST have identity.

ID Modelling

The rule is **scoped to the owning bounded context**. Inside the context that owns
an identity, it is a Value Object. Crossing a context boundary, it is a string.

**Own identities — MUST be Value Objects.**
- An identity owned by *this* bounded context MUST be modelled as a Value Object.
- It MUST NOT be a primitive type.
- It MUST be immutable, and MUST validate its own format at construction.
- Placement depends on reuse:
  - If only used inside the aggregate → may be defined inline.
  - If referenced across aggregates → define in a shared domain type package
    **within the bounded context** (not in `shared`).

Examples: `BookingId` in `booking.core.domain.tourbooking`, `GuideTourId` in
`guide.core.domain.guidetour`.

**Foreign identities — MAY be plain `String`.**

A reference to an identity **owned by another bounded context** MAY be carried as a
plain `String`. This is a deliberate exception, not an oversight, and it applies to
commands, results, domain events and aggregate state alike.

Rationale: the alternative is to promote every referenced identity into the shared
kernel so both contexts can share the type. That inverts the point of having
contexts — it makes them co-evolve through `shared`, so a change to one context's
identity format becomes a change to the other's compile-time dependencies. Treating
the boundary as a **serialization boundary** keeps the contexts independent, which is
worth more than type safety on a value the receiving context only ever stores and
echoes back.

The receiving context does not own the identity, cannot validate its invariants, and
must not reason about its structure. A `String` states that honestly; a Value Object
would imply knowledge the context does not have.

Example: `guideTourId` is owned by `guide` (as `GuideTourId`). Where `booking`
carries it — `TourBooking.markActive(Instant, String)`, `BookingActivated`,
`TourStarted` — it is a plain `String` correlation id.

Constraints on foreign identities:
- MUST be treated as opaque. No parsing, no substring, no format assumptions, no
  reconstructing the owning context's Value Object from it.
- MUST NOT carry business meaning in the receiving context. It is a correlation
  handle for tracing and for calling back through a port — never a value to branch on.
- SHOULD be named so the ownership is obvious (`guideTourId`, not `id`).
- If the receiving context starts enforcing rules about a foreign identity, that is a
  signal the boundary is wrong — raise it rather than promoting the type.

**Identities owned by no context in this system.**

An identifier belonging to an *external* system, referenced by more than one of our
contexts and owned by none of them, is a third category. It MAY live in `shared` as a
Value Object, because doing so couples our contexts to the external contract rather
than to each other — which is what the shared kernel is for
(`architecture.definition.md` § 9).

`shared.domain.TourId` is the only such case today: there is no `Tour` aggregate in
this system, the tour catalogue is external, and both contexts must validate the
reference identically. See `adr/0005-bounded-context-identity-boundaries.adr.md`.

This category is deliberately narrow. "Both contexts use it" is not sufficient
justification — the test is whether *neither* context owns it. If one does, the other
uses a `String`.


## Aggregate

*Definition:* A consistency boundary. The aggregate root enforces invariants and is the only entry point 
for modifications.

*Rules:*
- Only the Aggregate Root is referenced from outside the aggregate.
- One aggregate handles one transactional consistency set of invariants.
- Other entities inside the aggregate are not loaded/modified independently.
- Cross-aggregate rules are eventual or orchestrated (not enforced as a single invariant).

*Command rule of thumb:*
- One command → one aggregate root mutation (fast to implement, easy to reason about).
- If a use case needs multiple aggregates, that’s orchestration (application service) + eventual consistency.

Constraints:
- Aggregates are always valid.
- Aggregates do not perform IO.
- Aggregates do not depend on Spring/JPA.
- Aggregate methods enforce invariants
- Aggregate methods can return / record domain events
- Aggregates never call repositories, message buses, HTTP clients, clocks directly


### What an aggregate stores

**An aggregate stores what an invariant or an acceptance criterion must be able to
observe, and nothing else.** Everything else a transition is told belongs on the emitted
event, where whoever needs it can read it without the aggregate carrying state it never
consults.

Apply it by naming the observer. If you cannot name what reads the field — an invariant
that guards on it, an acceptance criterion that asserts it, a query the system owes an
answer to — it is event payload, not state.

Worked both ways, because the rule is not "prefer events":

| Field | Stored? | The observer |
|-------|---------|--------------|
| `TourBooking.cancelledAt` / `cancelledBy` / `cancellationReason` | **yes** | UC08 AC-06 must prove a pre-existing attribution survived a *rejected* second cancellation. The attempt throws, so no event is emitted and there is nothing but aggregate state to assert against |
| `BookingActivated.guideTourId`, `BookingCompleted.guideTourId`, `BookingCancelledByGuide.guideTourId` | no | nothing queries which guide tour caused a transition; no invariant guards on it |
| `startedAt` on `TourBooking` (UC06), `completedAt` on `TourBooking` (UC07) | no | another context's fact, relayed. No booking invariant compares against it |
| `GuideTour.startedAt` | **yes** | invariant I-07 compares `completedAt` against it |

The temptation this rule resists is storing a value because it was passed in and looks
like data. A column nothing reads still has to be migrated, mapped, round-tripped and
tested, and it invites a later reader to treat it as authoritative when the event was.

The temptation it also resists is the opposite one — dropping a field for symmetry with a
neighbouring use case. `TourBooking` stores its cancellation timestamp while discarding its
completion timestamp, and that asymmetry is correct: the two have different observers.
Consistency between use cases is not the criterion; the observer is.

Recorded after `ddd-hex-reviewer` observed that this criterion had decided UC06, UC07,
UC08 and UC09 while existing only in a use-case spec and a domain spec — authority levels
14 and 16 — which made the precedent unappealable and unenforceable. Same defect class
`architecture.definition.md` § 4.6 fixed for write-side query criteria.


## Aggregate Root

*Definition:* The entity that guards the aggregate boundary.  

*Rules:*
- Public behaviour methods live on the root.
- Root holds the domain events produced during changes (or returns them).

## Domain Service

*Definition:* Stateless domain logic that doesn’t naturally belong to a single aggregate.

*Rules:*
- Stateless and pure (no IO).
- Uses domain types (entities/value objects), not DTOs.

*When to use:*
- A rule uses multiple domain concepts but is still “pure domain logic”.
- Avoid turning domain services into “god classes”.

## Application Service (Use Case)

*Definition:* Orchestrates a use case: loads aggregates, invokes domain behavior, persists, publishes.

*Responsibilities:*
- Load aggregates via repositories.
- Execute domain behavior.
- Pass required external data as parameters.
- Apply authorization rules at a clearly defined boundary.
- Persist changes.
- Trigger event publication.

Application Services MUST NOT contain domain invariants.

If orchestration spans multiple aggregates over time or asynchronously,
a dedicated process manager / saga SHOULD be used.

## Factory

*Definition:* A domain creation component that ensures invariants at construction.

*Rules:*
- Prefer static named constructors on the aggregate/value object first.
- Use a factory when creation needs:
  - multiple steps
  - generation of IDs
  - collaboration of multiple values

*Constraints:*
- Factories should be immutable and thread-safe.
- Factories should not have side effects.

Factories are used to guarantee invariant-safe object creation.

*Creation Rules:*
- Prefer named constructors or static factory methods.
- Builders SHOULD only be used when:
- The object has many optional parameters, AND
- Readability significantly improves, AND
- Invariants are enforced at build() time.

Half-constructed domain objects MUST NOT exist.

All factories MUST ensure the object is valid upon creation.

## Repository

*Definition:* Repositories abstract persistence for aggregates.

*Rules:*
- Repositories return and persist aggregate roots, not JPA entities.
- Keep method names in domain language: findBy(OrderId), save(Order).
- MUST load, update, save, delete complete aggregates
- MUST delete complete aggregates with all attached entities and value objects.
- MUST persist aggregate state atomically.
- MUST NOT expose partial modification methods.

*Constraints:*
- Repository interface lives in the domain (or application core).
- Implementation lives in the infrastructure adapter (Spring Data / JPA / jOOQ).

## Domain Event

Domain Events

*Definition:* Domain Events represent facts that happened inside the domain.

*Rules:*
- MUST be immutable.
- MUST be part of the ubiquitous language.
- MUST describe something that already happened (past tense).

Domain Events are raised inside aggregates.

*Publication:*
- Application layer is responsible for publishing events AFTER successful transaction commit.

*Domain vs Integration Events:*

A distinction MUST be made between:
- Domain Event → internal to the bounded context.
- Integration Event → external communication contract.

Integration Events MAY be derived from Domain Events but are NOT the same concept.

## Commands & Queries

*Definition:* Commands and Queries represent application boundary inputs.

*Rules:*
- MUST be immutable.
- MUST NOT contain domain behavior.
- MUST represent intent (Command) or information request (Query).
- MUST NOT depend on infrastructure types.

If crossing process boundaries, they MUST be treated as versioned message contracts.

Serializable is NOT a requirement unless required by the transport mechanism.

## Read Models

*Definition:* Read Models are optimized representations for queries.
They are not part of the transactional aggregate model.

*Rules:*
- MAY denormalize data.
- MAY join multiple aggregates.
- MUST NOT contain domain invariants.
- MUST NOT mutate domain state.

Read Models exist to optimize query performance and projection use cases.

# Error model

- Use domain exceptions (or Result style) for business rule violations:
  - OrderAlreadyPaid, InsufficientStock
- Distinguish:
  - Domain errors (expected) vs technical errors (unexpected)
  - Domain errors should be mappable to API errors consistently.