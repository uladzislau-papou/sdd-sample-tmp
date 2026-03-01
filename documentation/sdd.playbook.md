# SDD Process Requirements

## Purpose

This document defines how Spec-Driven Development (SDD) operates in Alpine Booking.

SDD is not documentation overhead.
It is an enforcement mechanism.

No implementation exists without specification.
No architectural change exists without explicit decision recording.


# 1. Spec Hierarchie (Source of Truth)

See Definition in `file-usage.definition.md` for details.

Implementation must always reference at least one Use Case Spec.

No orphan code.



# 2. Mandatory Specification Types

The following specifications are required:
- Use Case Spec
- Domain Spec (for new or modified aggregates)
- Port Specification (if integration changes)
- ADR (when architectural decisions are involved)
- Test Definition (behavioral expectations)

If a change cannot be mapped to one of these, it must not be implemented.


# 3. Traceability Model

Traceability must be explicit:

Use Case → Aggregate → Invariants → Domain Events → Ports → Adapters → Tests

Every:
- REST endpoint must map to a Use Case.
- Use Case must reference affected Aggregates.
- Domain invariants must be test-covered.
- Outbound calls must go through Ports.
- Adapters must not contain business logic.

Tests must reflect domain behavior, not framework wiring.


# 4. Acceptance Criteria

Every implementation must define measurable acceptance criteria.

Format
```none
Given
When
Then
```

Acceptance Criteria must describe:
- Domain behavior
- State transitions
- Failure scenarios
- Invariant enforcement

Technical implementation details are not acceptance criteria.


# 5. Quality Gates (Merge Blockers)

The following are mandatory:
- ./gradlew clean test succeeds
- ./gradlew build succeeds
- All AssertJ tests pass
- No layering violations
- No business logic outside the domain layer
- No direct framework dependency inside domain
- No failing edge case tests

If any fails, then merge is blocked.


# 6. ADR Requirement

An ADR is mandatory when:
- Changing architectural layering
- Introducing new infrastructure
- Modifying transaction boundaries
- Introducing async/event-driven processing
- Changing persistence strategy
- Modifying package ontology
- Introducing caching or cross-cutting concerns

Architectural decisions must never be implicit.


# 7. Scope Control

To prevent architectural drift:
- No implementation without Spec
- No refactoring outside declared scope
- No cross-context leakage
- No direct repository access from controllers
- No framework annotations inside domain layer

Every change must remain within its declared bounded context.



# 8. Domain Governance Rules

The following are strict:
- Aggregates must be Always-Valid
- Invariants enforced inside constructors or state transition methods
- No setter-based mutation
- Domain Events emitted explicitly
- Domain layer contains no Spring annotations
- Persistence is an adapter concern

Violations invalidate the change.


# 9. Non-Goals

Alpine Booking does not aim to:
- Demonstrate framework tricks
- Maximize feature count
- Optimize prematurely
- Blur architectural boundaries

It exists to demonstrate disciplined, domain-centric backend architecture.