# Domain vs Use Case – Responsibility Boundary

## Purpose

This document clarifies the strict separation between:

- Domain Model
- Use Cases (Application Layer)

Confusion here leads to anemic models or transaction scripts.


## 1. Domain

The Domain contains:

- Aggregates
- Entities
- Value Objects
- Domain Services (rare)
- Domain Events

The Domain is responsible for:

- Enforcing invariants
- Modeling business rules
- State transitions
- Emitting domain events
- Guaranteeing Always-Valid state

The Domain does NOT:

- Access repositories
- Start transactions
- Call external systems
- Know about Spring
- Know about JPA
- Know about GraphQL

The Domain models truth.


## 2. Use Cases

Use Cases (Application Services, implemented as `*Driver`) are responsible for:

- Orchestrating domain behavior
- Loading and persisting aggregates
- Managing transactions
- Calling outbound ports
- Calling another context's inport, where the interaction model says so
- Mapping input/output DTOs

Use Cases do NOT:

- Implement business rules
- Contain invariants
- Modify aggregate state directly

They coordinate.


# 3. Quick Rule of Thumb

If logic answers:
“What is allowed?”
→ Domain.

If logic answers:
“In which order do we call things?”
→ Use Case.


# 4. The hard case in this domain: inherited terms

The rule of thumb is easy until a rule needs a value the aggregate does not hold.
Issuing a lease (UC05) must check that its price sits inside the master contract's
price band. Three readings, only one correct:

| Reading | Where the rule lives | Verdict |
|---------|---------------------|---------|
| The driver fetches the band and compares it to the price, then constructs the lease | Use case | **Wrong** — anemic. The driver is enforcing a business rule, and nothing stops the next caller skipping it |
| The aggregate holds a repository and fetches the band itself | Domain, with IO | **Wrong** — the aggregate performs IO and depends on another context |
| The driver fetches the band and **passes it in**; the aggregate compares and throws | Split, correctly | **Right** |

The third is what `modelling.definition.md` means by *policy*: external
information is passed as a parameter, and the aggregate still owns the decision.
The driver answers "where does the band come from"; the aggregate answers "is this
price allowed".

The test for whether you got it right: **delete the driver and ask whether the rule
still holds.** If a lease can be constructed outside the price band by calling the
aggregate directly, the rule was in the wrong place.


# 5. Anti-Patterns

- If validation logic lives in a GraphQL controller → wrong.
- If business rules live in a use case → wrong.
- If an aggregate requires a setter → wrong.
- If a use case changes internal fields → wrong.
- If an aggregate reaches for a value instead of being given it → wrong.

Boundary clarity is mandatory.
