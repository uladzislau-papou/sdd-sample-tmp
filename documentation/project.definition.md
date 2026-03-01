# Project Vision: Alpine Booking

## Purpose

Alpine Booking is a reference-grade Tour Booking system built to demonstrate:

- Spec Driven Development (SDD)
- Tactical Domain-Driven Design (DDD)
- Hexagonal Architecture
- Always-Valid Domain Models
- Explicit Transaction Boundaries

It is not a hotel booking clone.
It models guided alpine tours with limited capacity.

It is not feature-driven.
It is structure-driven.


## Problem

Tour booking systems appear simple but contain:

- Capacity constraints per tour date
- Strict lifecycle transitions
- Temporal validation rules
- Cross-aggregate availability checks
- Cancellation policies
- Concurrency risks under load

Most real-world implementations degrade into:

- Controller-driven logic
- Anemic domain models
- Validation chaos
- Transaction confusion
- Framework leakage into core logic

Alpine Booking demonstrates a disciplined alternative.


## Core Focus

The domain models:

- Tour booking lifecycle
- Capacity enforcement
- Participant count constraints
- Cancellation rules
- Strict state transitions
- Domain event emission

Correctness over convenience.


## Architectural Stance

- Strict Ports & Adapters
- Pure domain layer (no framework leakage)
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


## Non-Goals

- No hotel feature clone
- No UI playground
- No framework showcase
- No microservice sprawl
- No premature scalability patterns

Alpine Booking exists to prove that clean architecture scales conceptually before it scales technically.