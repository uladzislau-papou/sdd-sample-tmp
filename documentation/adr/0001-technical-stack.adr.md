# ADR 0001 – Core Technical Stack

## Status

**SUPERSEDED by ADR-0009.**

The decision text below is left untouched, as an accepted ADR's text must be. What changed
is the project: this repository became a Kotlin template, so the Java 21/jOOQ/H2 stack this
recorded no longer describes anything here. ADR-0009 records the stack that does, and
states which of the reasons below survived the change and which did not — the explicit
rejection of JPA is the one that was reconsidered, and ADR-0011 explains on what grounds.

## Context

Alpine Booking is a reference-grade backend system demonstrating:

- Spec Driven Development (SDD)
- Tactical Domain-Driven Design (DDD)
- Hexagonal Architecture
- Always-Valid Domain Models

The technical stack must support:

- Strong type-safety
- Explicit SQL control
- Clear architectural boundaries
- Deterministic migrations
- Fast feedback testing

## Decision

We adopt the following stack:

- Java 21
- Spring Boot 4.x
- Gradle (Kotlin DSL preferred)
- jOOQ for SQL access
- Flyway for schema migrations
- H2 for development and test database
- JUnit 5 + AssertJ for testing

We explicitly do NOT use:
- JPA
- Hibernate
- Spring Data repositories
- Reactive stack
- Implicit schema generation

## Rationale

### Java 21
Modern language features (records, sealed types) improve domain modeling.

### Spring Boot 4
Provides stable infrastructure wiring without leaking into the domain.

### jOOQ
- Type-safe SQL
- No ORM impedance mismatch
- Full control over queries
- Explicit data mapping

### Flyway
- Deterministic, versioned migrations
- Clear schema history
- No runtime schema guessing

### H2
- Lightweight
- Fast
- Sufficient for reference implementation

### AssertJ
- Expressive assertions
- Fluent domain test style

## Consequences

- Developers must write SQL explicitly.
- Persistence logic must be consciously implemented.
- No auto-magical persistence layer.

The system trades convenience for clarity.