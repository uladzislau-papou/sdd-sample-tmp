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

A domain model is defined within a single bounded context. The registry of contexts
is `architecture.definition.md` § 11.

A bounded context defines:
- The scope in which the model is consistent.
- The vocabulary used inside the model.
- The ownership of invariants.

External systems MUST NOT leak their models directly into the domain.
If necessary, mapping or anti-corruption logic MUST be used at the boundary.

This domain has more external systems than most: Odoo and Radar hold the partner
record, an employer directory holds the employer, a bike catalogue holds the leasing
object. `partner_number` exists purely as a join key between two of them. None of
their models enter the domain; each is referenced by an identity and nothing more.

# Validity & Invariants

## Always-Valid Principle

Domain objects (Entities, Aggregates, Value Objects) MUST be valid at all times.

## Definition of “valid”:

An object is considered valid if:
1.	All structural constraints are satisfied (type safety, non-null where required).
2.	All Value Object invariants hold.
3.	All state-dependent invariants hold according to the explicit state model.

“Always valid” does NOT mean “fully completed”.
Partial or evolving states are allowed if they are explicitly modelled (e.g. `DRAFT`,
`ACTIVE`, `CANCELLED`) and define their own invariants.

## State-Dependent Invariants

Invariants MAY depend on the current lifecycle state.

Example:
- `activationDate` MAY only be set if `status == ACTIVE`.
- A cancelled master leasing contract MUST NOT accept a configuration amendment.

State transitions MUST enforce invariants atomically.

## Policies vs Invariants

A clear distinction MUST be made between:
- Invariants → internal consistency rules of the aggregate.
- Business policies → rules depending on external systems, time, or cross-aggregate information.

Policies MUST NOT require infrastructure access inside the aggregate.
If required, external information MUST be passed as parameters or handled in Application Services.

This distinction does real work here. "The lease's price is within the master
contract's price band" looks like an invariant of the individual contract and is
not: the band belongs to another aggregate in another context. It is a policy, the
band is passed in as a parameter, and the aggregate enforces it against the value it
was given (UC05). The aggregate never reaches for it.

## Rehydration Rule

Rehydrating an object from persistence MUST NOT violate invariants.

If historical data violates current invariants, this MUST be handled explicitly via:
- Data migration
- Repair use cases
- Compatibility logic

Silent acceptance of invalid domain state is NOT allowed.

Concretely: `reconstitute` skips *creation-time* checks — a master contract whose
price band was widened last year must still load — but it does not skip structural
ones. A row that cannot produce a valid value object is a corrupt row and must fail
loudly rather than produce a half-built aggregate.

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

> **"Entity" is overloaded in this codebase and the two meanings must not be
> confused.** A *domain entity* is this building block. A `*Entity` class in
> `outbound.persistence.write` is a JPA row mapping and is not a domain object at all
> (`architecture.definition.md` § 4.6). `MlcConfiguration` is a domain entity;
> `MlcConfigurationEntity` is a row. They are different classes on purpose.

## Value Object

*Definition:* Immutable object defined by its values.

*Rules:*
- Immutable (`val` properties; no setters).
- Equality is by value.
- Validation happens at creation time (`init` block or factory).

*Constraints:*
- Use `data class` (`coding-style.definition.md` § 2.1).
- No IO.
- No back-references to entities/aggregates.
- Prefer dedicated types to `String`/`BigDecimal`/`UUID`.

### Money and percentages get types, and this is not optional

Almost every commercial term in this domain is an amount or a rate, and almost every
one of them is derived from another by arithmetic:

```
rate_per_month   = leasing_value × leasing_factor
residual_value   = leasing_value × buyout_calculation_factor
term_end         = term_start + term_months
early_claim_fee  = leasing_value × early_claim_fee_percentage
```

A `Double` anywhere in that chain is a defect. Binary floating point cannot represent
`0.01`, the error compounds across a 36-month term, and the result is a contract whose
stated monthly rate does not sum to its stated total. A bare `BigDecimal` is better and
still wrong, because nothing stops two amounts in different currencies being added.

So: amounts are `Money` (`BigDecimal` + `Currency`, scale fixed, arithmetic refusing
mixed currencies), rates and quotas are `Percentage`. `architecture.definition.md` § 10
lists the primitive form as a forbidden anti-pattern.

## Identity

*Definition:* A unique identifier for an entity or aggregate.

Entities and Aggregates MUST have identity.

### ID Modelling

The rule is **scoped to the owning bounded context**. Inside the context that owns
an identity, it is a Value Object. Crossing a context boundary, it is a string.

**Category 1 — Own identities. MUST be Value Objects.**
- An identity owned by *this* bounded context MUST be modelled as a Value Object.
- It MUST NOT be a primitive type.
- It MUST be immutable, and MUST validate its own format at construction.
- Placement depends on reuse:
  - If only used inside the aggregate → may be defined alongside it.
  - If referenced across aggregates → define in a shared domain type package
    **within the bounded context** (not in `shared`).

Examples: `MasterLeasingContractId` in `masterleasing.core.domain.masterleasingcontract`,
`IndividualLeasingContractId` in `individualleasing.core.domain.individualleasingcontract`.

**Category 2 — Foreign identities from a peer context. MAY be plain `String`.**

A reference to an identity **owned by another bounded context in this system** MAY be
carried as a plain `String`. This is a deliberate exception, not an oversight, and it
applies to commands, results, domain events and aggregate state alike.

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

Example: `masterLeasingContractId` is owned by `masterleasing` (as
`MasterLeasingContractId`). Where `individualleasing` carries it — on the aggregate,
on `IssueIndividualLeasingContractCommand`, on
`IndividualLeasingContractTerminatedByMasterContract` — it is a plain `String`.

Constraints on category 2:
- MUST be treated as opaque. No parsing, no substring, no format assumptions, no
  reconstructing the owning context's Value Object from it.
- MUST NOT carry business meaning in the receiving context. It is a correlation
  handle for tracing and for calling back through the owner's inport — never a value
  to branch on.
- SHOULD be named so the ownership is obvious (`masterLeasingContractId`, not `id`).
- If the receiving context starts enforcing rules about a foreign identity, that is a
  signal the boundary is wrong — raise it rather than promoting the type.

**Category 3 — Identities owned by no context in this system, referenced by more than
one. MAY be a shared Value Object.**

An identifier belonging to an *external* system, referenced by more than one of our
contexts and owned by none of them, MAY live in `shared` as a Value Object, because
doing so couples our contexts to the external contract rather than to each other —
which is what the shared kernel is for (`architecture.definition.md` § 9).

`shared.domain.EmployerId` and `shared.domain.LessorId` are the members today. Both
appear on `MASTER_LEASING_CONTRACT` and on `INDIVIDUAL_LEASING_CONTRACT`, both refer
to records in systems outside this one, and both carry an invariant the two contexts
must enforce identically.

This category is deliberately narrow. "Both contexts use it" is not sufficient
justification — the test is whether *neither* context owns it. If one does, the other
uses a `String`.

**Category 4 — External identities referenced by exactly one context. Value Object,
inside that context.**

An identifier belonging to an external system that only *one* of our contexts
references is a Value Object in that context. It is not shared, because sharing a
type nothing else uses grows the kernel for nothing; and it is not a `String`,
because category 2's argument does not apply — there is no peer context whose
independence a `String` is protecting, and
`architecture.definition.md` § 10's prohibition on primitives for meaningful concepts
applies with full force.

Examples, all in `individualleasing`: `JobCyclistId` (the employee), `BikeId` (the
leasing object / *Leasingobjekt*), `KauNumber` (the *Antragsnummer* the lease was
created from).

> This category is an addition to the three the framework was inherited with, and it
> exists because this domain has something the reference domain did not: external
> references used by one context only. Under the three-category rule those fields had
> no home — category 2 requires a peer context, category 3 requires two of ours, and
> category 1 requires ownership we do not have. Left unresolved, the default would have
> been a bare `String` for `bikeId` and `jobCyclistId` "because they are foreign",
> which is category 2's conclusion reached without category 2's reason. See
> `adr/0005-bounded-context-identity-boundaries.adr.md`.

### The four categories, decided

| Identity | Owned by | Category | Modelled as |
|----------|----------|----------|-------------|
| `MasterLeasingContractId` | `masterleasing` | 1 | VO in `masterleasing` |
| `IndividualLeasingContractId` | `individualleasing` | 1 | VO in `individualleasing` |
| `ElvNumber` | `individualleasing` | 1 | VO in `individualleasing` |
| `masterLeasingContractId` *inside `individualleasing`* | `masterleasing` | 2 | `String` |
| `EmployerId` | external (directory) | 3 | VO in `shared.domain` |
| `LessorId` | external (partner master) | 3 | VO in `shared.domain` |
| `PartnerNumber` | external (Odoo ↔ Radar join key) | 4 | VO in `masterleasing` |
| `JobCyclistId` | external (employee record) | 4 | VO in `individualleasing` |
| `BikeId` | external (bike catalogue) | 4 | VO in `individualleasing` |
| `KauNumber` | external (sales order) | 4 | VO in `individualleasing` |

## Aggregate

*Definition:* A consistency boundary. The aggregate root enforces invariants and is the only entry point
for modifications.

*Rules:*
- Only the Aggregate Root is referenced from outside the aggregate.
- One aggregate handles one transactional consistency set of invariants.
- Other entities inside the aggregate are not loaded/modified independently.
- Cross-aggregate rules are eventual or orchestrated (not enforced as a single invariant).

*Command rule of thumb:*
- One command → one aggregate root mutation.
- If a use case needs multiple aggregates, that’s orchestration (application service) +
  eventual consistency, or the narrow shared-transaction case in
  `architecture.definition.md` § 10.

Constraints:
- Aggregates are always valid.
- Aggregates do not perform IO.
- Aggregates do not depend on Spring/JPA.
- Aggregate methods enforce invariants.
- Aggregate methods record domain events.
- Aggregates never call repositories, message buses, HTTP clients, clocks directly.

### Why the configuration is inside the aggregate, not beside it

`MLC_CONFIGURATION` is a separate table with its own id, and the obvious reading of
the data model is two aggregates with a foreign key. It is modelled as **one
aggregate** — `MasterLeasingContract` holding its `MlcConfiguration` — and the reason
is the definition above: a consistency boundary is drawn around the invariants that
must hold together.

Amending a configuration is not an independent act. It produces a new version, the
contract must point at exactly one current version, and whether the amendment is
permitted at all depends on the contract's status. Those three facts must be true
together or not at all, which is the definition of one consistency boundary. Two
aggregates would make "the contract's current configuration" eventually consistent
with itself.

The same argument holds on the individual side for `ILC_CONFIGURATION`.

What is *not* inside the boundary: the `parent_mlc_id` link between a base contract
and an affiliated one. That is a reference between two aggregates of the same type and
is carried as an id, never as an object reference.

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
| `MasterLeasingContract.cancelledDate` / `cancellationReason` | **yes** | the cancellation must be re-readable, and UC04 requires that a second cancellation does not overwrite the first attribution. The second attempt throws, so no event is emitted and there is nothing but aggregate state to assert against |
| `MasterLeasingContract.activationDate` | **yes** | it bounds every lease issued under the contract: UC05 rejects a `termStart` before it |
| `IndividualLeasingContract.terminatedAt` / `terminatedBy` | **yes** | same argument as the master contract's — attribution is state the lease owns |
| `masterLeasingContractId` on `IndividualLeasingContractTerminatedByMasterContract` | no | nothing queries which cancellation caused a termination; no invariant guards on it |
| `activatedAt` relayed from `MasterLeasingContractActivated` into UC06 | no | another context's fact, relayed. No individual-contract invariant compares against it |

The temptation this rule resists is storing a value because it was passed in and looks
like data. A column nothing reads still has to be migrated, mapped, round-tripped and
tested, and it invites a later reader to treat it as authoritative when the event was.

The temptation it also resists is the opposite one — dropping a field for symmetry with a
neighbouring use case. Consistency between use cases is not the criterion; the observer is.

## Aggregate Root

*Definition:* The entity that guards the aggregate boundary.

*Rules:*
- Public behaviour methods live on the root.
- Root holds the domain events produced during changes and exposes them for draining.

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
- Prefer named factory functions on the aggregate/value object's companion object first.
- Use a separate factory when creation needs:
  - multiple steps
  - generation of IDs
  - collaboration of multiple values

*Constraints:*
- Factories should be immutable and thread-safe.
- Factories should not have side effects.

*Creation Rules:*
- Prefer named factory functions.
- Builders SHOULD only be used when the object has many optional parameters, readability
  significantly improves, AND invariants are enforced at `build()` time. Kotlin's named
  and default arguments usually make a builder unnecessary; reach for them first.

Half-constructed domain objects MUST NOT exist.

All factories MUST ensure the object is valid upon creation.

## Repository

*Definition:* Repositories abstract persistence for aggregates.

*Rules:*
- Repositories return and persist aggregate roots, not JPA entities.
- Keep method names in domain language: `findById(MasterLeasingContractId)`, `save(contract)`.
- MUST load, update, save, delete complete aggregates.
- MUST delete complete aggregates with all attached entities and value objects.
- MUST persist aggregate state atomically.
- MUST NOT expose partial modification methods.

*Constraints:*
- The repository **interface** lives in `core.outport`.
- The implementation lives in `outbound.persistence.write`, and is the only place that
  knows JPA exists.
- A repository MUST NOT return a Hibernate-managed entity, directly or via a mapped
  aggregate that shares references with one. The aggregate the core receives is detached
  data; if a caller's mutation reached the database without an explicit `update`, the
  transaction boundary in the driver would be decorative.

## Domain Event

*Definition:* Domain Events represent facts that happened inside the domain.

*Rules:*
- MUST be immutable.
- MUST be part of the ubiquitous language.
- MUST describe something that already happened (past tense).

Domain Events are raised inside aggregates and drained by the application layer.

*Publication:*
- Application layer is responsible for publishing events AFTER successful transaction commit.

*Domain vs Integration Events:*

A distinction MUST be made between:
- Domain Event → internal to the bounded context, lives in
  `<context>.core.domain.<aggregate>.event`.
- Integration Event → crosses a context boundary, lives in `shared.domain.event`.

Integration Events MAY be derived from Domain Events but are NOT the same concept.

Placement is decided by **who consumes it, not who emits it**.
`MasterLeasingContractActivated` is in `shared.domain.event` because
`individualleasing` listens for it. `MasterLeasingContractCancelled` is *not*, even
though it is the more significant fact, because no other context consumes it — UC04
propagates cancellation by calling an inport inside one transaction, not by
publishing (`architecture.definition.md` § 10). Promoting it to `shared` "for
symmetry" would put a type in the shared kernel that no consumer needs.

## Commands & Queries

*Definition:* Commands and Queries represent application boundary inputs.

*Rules:*
- MUST be immutable.
- MUST NOT contain domain behavior.
- MUST represent intent (Command) or information request (Query).
- MUST NOT depend on infrastructure types.

If crossing process boundaries, they MUST be treated as versioned message contracts.

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

- Use domain exceptions for business rule violations, named after the rule:
  `CreditLimitExceededException`, `InvalidMasterLeasingContractStateException`.
- Distinguish:
  - Domain errors (expected) vs technical errors (unexpected)
  - Domain errors must be mappable to a GraphQL error classification consistently
    (`architecture.definition.md` § 4.5)
- The exception type is part of the contract. Widening one to cover a second condition
  makes two failures indistinguishable to a client that has to handle them differently.
