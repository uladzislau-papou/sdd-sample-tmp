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
- Know about jOOQ

The Domain models truth.


## 2. Use Cases

Use Cases (Application Services) are responsible for:

- Orchestrating domain behavior
- Loading and persisting aggregates
- Managing transactions
- Calling outbound ports
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


# 4. Anti-Patterns

- If validation logic lives in controller → wrong.
- If business rules live in use case → wrong.
- If aggregate requires setter → wrong.
- If use case changes internal fields → wrong.

Boundary clarity is mandatory.