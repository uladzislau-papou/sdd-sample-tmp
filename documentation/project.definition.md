# Project Vision: JRL Contract Management

## Purpose

JRL Contract Management is a reference-grade Contract Management system built to
demonstrate:

- Spec Driven Development (SDD)
- Tactical Domain-Driven Design (DDD)
- Hexagonal Architecture
- Always-Valid Domain Models
- Explicit Transaction Boundaries

It is not a CRM clone.
It models bicycle salary-sacrifice leasing contracts at two levels: the master
agreement an employer signs with the lessor, and the individual lease each
employee takes under it.

It is not feature-driven.
It is structure-driven.


## Problem

Leasing contract systems appear to be plain CRUD and are not:

- Two contract levels, where the lower one inherits terms from the upper
- Versioned configurations, because the terms change while the contract lives
- Inheritance that is sometimes a live link and sometimes a one-time copy
- Corporate-group structures: affiliated contracts hanging off a base contract
- Commercial limits — credit exposure, eligible headcount, price bands — that
  must hold at the moment a lease is issued and not merely on a report
- Cancellation that cascades from the master contract down to live leases
- Notice periods, return quotas and early-claim fees, all of them date arithmetic
  that is wrong in a way nobody notices for a year

Most real-world implementations degrade into:

- Service-layer logic operating on database rows
- Anemic domain models
- Validation chaos
- Transaction confusion
- Framework leakage into core logic

JRL Contract Management demonstrates a disciplined alternative.


## Core Focus

The domain models:

- Master leasing contract (LRV) lifecycle
- Individual leasing contract (ELV) lifecycle
- Configuration versioning on both levels
- Term inheritance from master to individual contract
- Commercial constraint enforcement at issue time
- Strict state transitions
- Domain event emission

Correctness over convenience.


## Architectural Stance

- Strict Ports & Adapters
- Pure domain layer (no framework leakage — in particular, **no JPA annotation
  ever reaches an aggregate**)
- Aggregates enforce invariants
- Domain services only for cross-aggregate rules
- Explicit internal domain events
- External integration events are mapped, never leaked
- One use case = one transaction boundary
- Persistence is an implementation detail

Clarity over speed.
Structure over shortcuts.


## Development Doctrine

- Spec first
- Domain spec before implementation
- ADR before architectural change
- Small, verifiable increments
- AssertJ-based domain tests
- No business logic outside the domain
- Always-valid aggregate model


## Scope

Implemented in code: `MASTER_LEASING_CONTRACT` with `MLC_CONFIGURATION`, and
`INDIVIDUAL_LEASING_CONTRACT` with `ILC_CONFIGURATION`.

Specified but deliberately **not** implemented: `SERVICE_AGREEMENT` (DLV),
`UEV_CONTRACT` (ÜV) and `DOCUMENT`. They are in the data model and they are real,
but the two contract levels and the inheritance between them are what make this
domain worth modelling carefully. Adding three more aggregates would multiply the
surface area without exercising a single additional architectural rule. The specs
record the boundary so that "not built" is visibly a decision rather than an
oversight.


## Non-Goals

- No CRM feature clone
- No UI playground
- No framework showcase
- No microservice sprawl
- No premature scalability patterns
- No billing, dunning or accounting — a leasing rate is calculated here and
  charged elsewhere

JRL Contract Management exists to prove that clean architecture scales
conceptually before it scales technically.
