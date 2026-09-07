# Project Vision: the Tour Booking example

## What this document is

The **highest-ranked document** in `CLAUDE.md`'s authority order — and in this repository it
defines the **worked example**, not the template.

That distinction is deliberate. This repository is two things at once: a template for
starting Kotlin services, and a small running service that proves the template compiles and
that its gates fire. If document number one described the template, then every service
started from it would open with a document about its own scaffolding rather than about the
service being built — a small, permanent corruption of context in every agent run.

So:

| Question | Answered in |
|----------|-------------|
| What is this repository? | `README.md` |
| What is the example inside it? | **this file** |
| What will a new service fill in? | `project.definition.template.md` |

`initService` replaces this file with that template.


## Purpose of the example

A minimal Tour Booking service, built to demonstrate:

- Spec Driven Development (SDD)
- Tactical Domain-Driven Design
- Hexagonal Architecture with two delivery transports over one core
- Always-Valid domain models
- Explicit transaction boundaries
- Gates that enforce all of the above instead of describing them

**The domain is the vehicle, not the product.** When judging a change to the example, "does
this demonstrate the method better?" outranks "does this make the booking system more
complete."

The domain is also deliberately foreign to whatever you are building. That is a feature: a
concrete domain teaches better than an abstract `Order`/`Item`, and one that is obviously
not yours will not be copied into a real service by inertia.


## Why a booking domain

It looks simple and is not. It contains, in a few hundred lines:

- capacity constraints per tour date
- strict lifecycle transitions with terminal states
- temporal validation, which forces the question of where "now" comes from
- an external availability check, so there is a real outbound port
- two bounded contexts that must not import each other
- a cross-context domain event, and an inbound adapter that is not an API

Most implementations of exactly this degrade into controller-driven logic, anemic models,
validation scattered across layers, unclear transactions and framework types in the core.
Every one of those degradations is what a rule in this repository exists to prevent, and
each has a test that fails when it happens.


## Scope of the example

Four use cases across two bounded contexts:

| Use case | Context | What it demonstrates |
|----------|---------|----------------------|
| UC01 `RequestTourBooking` | `booking` | aggregate creation, an outbound port, **one core reached over both REST and GraphQL** |
| UC02 `ConfirmTourBooking` | `booking` | a state transition over REST |
| UC05 `StartTour` | `guide` | a state transition that publishes a cross-context event |
| UC06 `MarkBookingActive` | `booking` | an inbound adapter driven by an **event, not a request** — no API, and a § 9 that says so |

They form a connected chain: `REQUESTED → CONFIRMED → ACTIVE`, where the last step is
triggered by the other context. That connectedness was a deliberate correction — an earlier
cut of three use cases left `CONFIRMED` unreachable, so UC06's happy path could not be
reached end to end even though its unit tests passed.


## Architectural stance

- Strict Ports & Adapters
- A framework-free core, enforced rather than requested
- Aggregates enforce their invariants; no setters, no `data class` on an entity
- One use case = one transaction boundary, owned by the driver
- Domain events published after commit
- Contexts communicate through `shared.domain.event` or a published inport, never internals
- Persistence is an implementation detail, and the domain may not know which one


## Development doctrine

- Spec first. No implementation without one.
- ADR before an architectural change.
- No production code without a failing test.
- Small, verifiable increments.
- When a rule rots, give it an executable owner rather than restating it.


## Non-Goals of the example

These are **limitations of the example, not architectural positions.** They are written
down so that a service built from this template does not mistake an absence for a decision
and invent its own answer three different ways.

| Absent | Consequence you should know about |
|--------|-----------------------------------|
| **No read side** | No query ports, no projections, no CQRS. GraphQL is used for a mutation only, and the schema's mandatory `Query` root carries one infrastructure field. The first service needing reads decides the pattern — and should write an ADR, because three services inventing it separately is the outcome this note exists to prevent |
| **No authentication or authorization** | No JWT, no roles, no tenant scoping. Every endpoint is open |
| **No PII and no secrets** | Which is why `/code-review`'s security axis has nothing to find here and ships unexercised |
| **No optimistic locking** | No `@Version`. Concurrent updates are last-write-wins |
| **No real outbound integration** | `StubAvailabilityChecker` always reports unlimited capacity, so the capacity invariant is exercised by tests but never at runtime |
| **No scheduling use case** | A `GuideTour` arrives in the database already scheduled |
| **No observability stack** | No metrics, tracing or structured audit trail |

Also not goals: no microservice split, no premature scalability patterns, no framework
showcase.


## What "done" means for this repository

Not "the booking system is complete" — it never will be. Done is:

- the example builds and every gate is green, including the ones that read the documentation
- every rule in the definitions has either an executable owner or an admission that it does
  not (`coding-style.definition.md` § 9)
- `initService` turns this into a new service that builds and passes its tests
