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
- A `Contract` MAY only be added while its `Master` is `ACTIVE`.
- A `Master` MUST reject a contract number one of its contracts already carries.

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

Examples: `MasterId` and `ContractId` in `contract.core.domain.master`. Both are inside the
same aggregate, so both are defined there — `ContractId` is not "shared" merely because two
types mention it.

**Identities owned by no context of ours — a Value Object, local by default.**

An external system's identifier is owned by none of our contexts.
`adr/0005-bounded-context-identity-boundaries.adr.md` calls this **category 3** and permits it
to live in `shared.domain` as a Value Object *when it is referenced by more than one of our
contexts*. Both conditions have to hold; "more than one context uses it" is not the test on its
own.

When only one context references it, the identity is a Value Object **local to that context**,
exactly like an own identity, and it is **not** promoted to `shared`. Promoting it later, when
a second context genuinely needs it, is a new ADR.

**This rule currently has no subject, twice over.** The service has one bounded context
(`adr/0024`) and no foreign identities at all, so category 3 is empty and the shared kernel
holds no identity type. `adr/0023` was the one time the rule was exercised — it declined to
promote three identities — and it was withdrawn with the domain that produced them. ADR-0005's
reservation is therefore unspent again.

Example, historical: `EmployerId`, `LessorId` and `PartnerNumber`, per
`adr/0023-participant-identities-are-context-local.adr.md`. Those types no longer exist, and
the ADR is withdrawn; it is cited because it is the only worked application of this rule the
repository has.

> **This clause was missing and the gap was load-bearing.** ADR-0005 laid out three
> categories, and a reader looking for where to put an external identity referenced by one
> context found a two-item menu: `shared` (whose condition was unmet) or a plain `String`
> (whose permission is scoped to *another of our* contexts and so unavailable). ADR-0023 argued
> the third option and set the precedent; `ddd-hex-reviewer` then pointed out that the
> precedent lived only in an ADR, so the next author would read the menu and pick from it.
> ADR-0005 states that this section is what it amends, which is why the clause belongs here.

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

There is no current example: the service has one bounded context (`adr/0024`), so no identity
is foreign to the context holding it. The shape to look for is an aggregate in context A
carrying context B's identity as a plain `String` on a method parameter, an event payload or a
field — never as B's Value Object.

Constraints on foreign identities:
- MUST be treated as opaque. No parsing, no substring, no format assumptions, no
  reconstructing the owning context's Value Object from it.
- MUST NOT carry business meaning in the receiving context. It is a correlation
  handle for tracing and for calling back through a port — never a value to branch on.
- SHOULD be named so the ownership is obvious (`billingAccountId`, not `id`).
- If the receiving context starts enforcing rules about a foreign identity, that is a
  signal the boundary is wrong — raise it rather than promoting the type.

**Identities owned by no context in this system.**

An identifier belonging to an *external* system, referenced by more than one of our
contexts and owned by none of them, is a third category. It MAY live in `shared` as a
Value Object, because doing so couples our contexts to the external contract rather
than to each other — which is what the shared kernel is for
(`architecture.definition.md` § 9).

**There is no such case today**, and `shared.domain` holds no identity at all. The category
has been exercised exactly once in this repository's history, by an identity for an external
catalogue that two contexts both had to validate identically. See
`adr/0005-bounded-context-identity-boundaries.adr.md`, whose reservation — a second member
requires its own ADR — is therefore unspent.

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
| `Master.status` | **yes** | invariant I-09 guards on it: a contract may be added only while the Master is `ACTIVE`, so the aggregate has to be able to answer the question |
| `Master.createdAt` | **yes** | UC01's AC-01 asserts it, and UC02 returns it. An acceptance criterion is an observer |
| `Contract.period` | **yes** | I-04 guards it at construction, and UC02 returns it |
| `MasterCreated.occurredAt` | no | it is the event's own timestamp, not the aggregate's state. `Master.createdAt` is the stored fact; the event carries a copy for its consumer |
| a "last modified" timestamp on `Master` | no | nothing queries it, no invariant guards on it, and no criterion asserts it. UC03 changes a Master and stores no trace of when — `project.definition.md` lists audit as a Non-Goal |

The last row is the one that costs something to hold to. A modification timestamp is the most
natural field in the world to add, it looks like data, and nothing in the model asks for it.

The temptation this rule resists is storing a value because it was passed in and looks like
data. A column nothing reads still has to be migrated, mapped, round-tripped and tested,
and it invites a later reader to treat it as authoritative when the event was.

The temptation it also resists is the opposite one — dropping a field for symmetry with a
neighbouring use case.

> Recorded after `ddd-hex-reviewer` observed that this criterion had decided four use cases
> while existing only in a use-case spec and a domain spec — authority levels 14 and 16 —
> which made the precedent unappealable and unenforceable. Same defect class
> `architecture.definition.md` § 4.6 fixed for write-side query criteria.
>
> The examples above were rewritten when the template's example was reduced; the original
> table's clearest row concerned a cancellation attribution that had to survive a *rejected*
> second cancellation — no event is emitted by a rejected transition, so aggregate state was
> the only thing an acceptance criterion could assert against. That use case is not part of
> the example any more, but it remains the sharpest illustration of the rule: **a transition
> that throws emits nothing, so anything a test must observe about it has to be state.**

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
- Repositories return and persist aggregate roots, never persistence entities. Enforced:
  `ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType`.
- Keep method names in domain language: findBy(OrderId), save(Order).
- MUST load, update, save, delete complete aggregates
- MUST delete complete aggregates with all attached entities and value objects.
- MUST persist aggregate state atomically.
- MUST NOT expose partial modification methods.

*Constraints:*
- The repository interface lives in `core.outport`.
- Implementation lives in an outbound adapter. Which persistence technology is a project
  choice (`technical.spec.md`); that no persistence type reaches the core is not
  (ADR-0011).

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