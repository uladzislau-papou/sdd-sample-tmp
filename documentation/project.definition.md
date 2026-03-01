# Project Vision: Alpine Booking

## Purpose

Alpine Booking is a reference-grade booking system built to demonstrate:
- Spec Driven Development (SDD)
- Tactical Domain-Driven Design (DDD)
- Hexagonal Architecture
- Always-Valid Domain Models

It is not feature-driven.
It is structure-driven.


## Problem

Booking systems look simple but contain:
- Temporal constraints
- Capacity limits
- State transitions
- Payment dependencies
- Concurrency risks

Most implementations collapse into controller logic and anemic services.

Alpine Booking demonstrates a disciplined alternative.

## Core Focus

The domain models:
- Reservation lifecycle
- Date overlap validation
- Capacity enforcement
- Cancellation rules
- Payment confirmation dependency

Correctness over convenience.

## Architectural Stance

- Strict Ports & Adapters
- Pure domain layer (no framework leakage)
- Aggregates enforce invariants
- Explicit domain events
- Persistence is an implementation detail

Clarity over speed.
Structure over shortcuts.


## Development Doctrine

- Spec first
- ADR before architectural change
- Small, verifiable increments
- AssertJ-based domain tests
- No business logic outside the domain


## Non-Goals
- No feature playground
- No framework showcase
- No microservice sprawl

Alpine Booking exists to prove that clean architecture scales conceptually before it scales technically.