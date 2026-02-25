# Modelling Definition

For this project, we model strictly according to the DDD building blocks.

Purpose: This handbook defines what we mean by DDD building blocks in this codebase, 
so modelling stays consistent, reviewable, and automatable.

*Core principles:*
- Model the business in the Domain layer. Infrastructure is replaceable.
- Business rules live where they are enforced (mostly aggregates/value objects).
- Prefer small aggregates with clear consistency boundaries.
- Prefer explicit types (value objects) over primitives for meaningful concepts.


# Building blocks

## Entity

*Definition:* Domain object with an identity that persists across state changes.

*Rules:*
- Has an ID (usually an ID Value Object).
- Equality is by identity, not by all fields.
- Mutable lifecycle is allowed (via behaviour methods), but invariants must always hold.

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

*ID Value Objects*
Use IDs as value objects when identity appears in more than one place or you want type-safety:
- OrderId, CustomerId, etc.
- Wrap UUID (or String) and validate the format.
- Define the IDs always as inline public record inside the entity it belongs to.


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

*Rules:*
- Applies authorisation and transaction boundaries.
- Maps input (command) → domain objects.
- Uses repositories and outbound ports.
- Does not contain business rules; it coordinates them.

*Constraints:*
- Application services can depend on Spring (optional), but should be easy to test without Spring.


## Factory

*Definition:* A domain creation component that ensures invariants at construction.

*Rules:*
- Prefer static named constructors on the aggregate/value object first.
- Prefer always the Builder-Pattern if applicable.
- Use a factory when creation needs:
- multiple steps
- generation of IDs
- collaboration of multiple values

*Constraints:*
- Factories should be immutable and thread-safe.
- Factories should not have side effects.

## Repository

*Definition:* Collection-like interface for aggregate roots.

*Rules:*
- Repositories return and persist aggregate roots, not JPA entities.
- Keep method names in domain language: findBy(OrderId), save(Order).

*Constraints:*
- Repository interface lives in the domain (or application core).
- Implementation lives in the infrastructure adapter (Spring Data / JPA / jOOQ).

## Domain Event

*Definition:* Something that happened in the domain.

*Rules:*
- Past tense naming: OrderPlaced, PaymentCaptured.
- Domain events are created by the domain model (usually aggregates).
- Two kinds:
- Internal: within the same bounded context/process
- External: published for other systems/contexts

*Publication rule:*
- Domain model only records events.
- Application layer decides when/how to publish (usually after commit).

## Command

*Definition:* Request to perform a use case (imperative intent).

*Rules:*
- Named as intent: PlaceOrderCommand.
- Minimal required fields only.
- Validate:
- syntactic validation at the boundary (API)
- semantic/domain validation in domain objects/aggregate

*Constraints:*
- Command objects are immutable.
- Command objects are serializable (for distributed systems).


## Query

*Definition:* Read-side request that does not mutate domain state.

*Rules:*
- Keep read models pragmatic. They don’t have to match the write model.

*Constraints:*
- Query objects are immutable.
- Query objects are serializable (for distributed systems).


## Error model

- Use domain exceptions (or Result style) for business rule violations:
- OrderAlreadyPaid, InsufficientStock
- Distinguish:
- Domain errors (expected) vs technical errors (unexpected)
- Domain errors should be mappable to API errors consistently.

Time, randomness, and “now”
•	Never call Instant.now() in the domain.
•	Use an outbound port ClockPort or provide time from application service.

⸻

Anti-patterns (explicitly forbidden)
•	JPA annotations in domain objects.
•	Calling repositories from aggregates/entities/value objects.
•	Using primitives for domain concepts when they carry meaning (money, ids, email, etc.).
•	Cross-aggregate invariants enforced inside one transaction “because it’s convenient”.

⸻