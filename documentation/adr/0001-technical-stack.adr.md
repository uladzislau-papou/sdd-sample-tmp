# ADR 0001 – Core Technical Stack

## Status
Accepted

## Context

JRL Contract Management is a reference-grade backend system demonstrating:

- Spec Driven Development (SDD)
- Tactical Domain-Driven Design (DDD)
- Hexagonal Architecture
- Always-Valid Domain Models

The technical stack must support:

- Strong type-safety, especially around nullability and money
- Deterministic migrations
- Clear architectural boundaries
- Fast feedback testing
- Alignment with the surrounding service landscape, so that engineers moving
  between JRL services are not also moving between stacks

That last constraint is not a technical one and is load-bearing anyway. This
service sits beside `risk-management-service`, which is Kotlin on Spring Boot 4
with JPA and PostgreSQL. A reference implementation that demonstrates good
architecture in a stack nobody else in the organisation runs demonstrates less
than it appears to.

## Decision

We adopt the following stack:

- **Kotlin 2.3** on **JVM 17** (ADR 0006)
- **Spring Boot 4.x** on Spring Framework 7
- **Gradle 9**, Kotlin DSL, via the wrapper
- **Spring GraphQL** as the only inbound transport (ADR 0008)
- **Spring Data JPA / Hibernate** for persistence, with the aggregate and the
  entity as separate classes (ADR 0009)
- **Flyway** for schema migrations, owning all DDL
- **PostgreSQL 17** in every environment including tests
- **JUnit 5 + AssertJ** for testing, with no mocking framework
- **ArchUnit** for architecture enforcement (ADR 0007)
- **Spotless/ktlint** and **detekt** for formatting and static analysis
- **JaCoCo** for a coverage report, not a gate

We explicitly do NOT use:
- A REST adapter
- An in-memory database for tests
- ORM-generated DDL (`ddl-auto` is `validate`)
- Open Session in View
- A mocking framework
- Lombok, or any annotation processor in the domain

## Rationale

### Kotlin

The decisive property is **nullability in the type system**. This domain is full
of fields that are legitimately absent — `cancelled_date` on a live contract,
`parent_mlc_id` on a base contract, an optional cancellation reason — and full of
fields that must never be absent. A language that cannot tell those apart forces
either `Optional` wrappers or a convention, and conventions decay.

It also makes the Always-Valid doctrine cheaper to hold: `data class` with an
`init` block is a validated value object in five lines, which means value objects
get created instead of being deferred into a `String`.

Secondary: `when` exhaustiveness over sealed types and enums turns "we added a
status and forgot to handle it" from a runtime bug into a compile error, and this
domain has four state machines.

### Spring Boot 4

Provides stable infrastructure wiring without leaking into the domain, and matches
the version the neighbouring service runs.

### JPA — chosen with a known cost

JPA is the least comfortable choice here and was made deliberately.

Against it: an ORM's value proposition is that your domain classes *are* your
rows, and accepting that offer ends the framework-free claim. Hexagonal
architecture and JPA pull in opposite directions by construction.

For it: it is what the organisation runs, the relational model here is
straightforward (no exotic queries, no reporting workload), and Spring Data
removes a large amount of boilerplate for the CRUD-shaped half of the work.

The resolution is ADR 0009: two class hierarchies with an explicit mapper. That
gives up the ORM's main convenience and keeps its remaining ones. The cost is
real — every field appears in three places — and is accepted.

The alternative considered was jOOQ, which is a better architectural fit (explicit
SQL, no lifecycle, no proxies, nothing that wants to be the domain model). It was
rejected on the organisational constraint above: it would make this the only
service in the landscape whose persistence layer nobody else can read.

### Flyway

- Deterministic, versioned migrations
- Clear schema history
- No runtime schema guessing

`ddl-auto=validate` is the other half. Flyway owns the schema; Hibernate checks
its mappings against it and refuses to start on a mismatch. `update` would make
the schema a side effect of the code.

### PostgreSQL everywhere, including tests

The reasoning is in `technical.spec.md`, Persistence Strategy. In short: with JPA
the dialect is not an implementation detail the tests are insulated from. It
decides enum storage, `numeric` rounding, `timestamptz` round-tripping and what a
constraint violation throws — and monetary rounding is exactly where this domain's
defects live.

### AssertJ, and no mocking framework

- Expressive assertions, fluent domain test style
- `test.definition.md` § 2.2 requires stubs over mocks. Not putting a mocking
  library on the classpath makes the discouraged path unavailable rather than
  merely discouraged, which is a stronger guarantee than a rule

## Consequences

- Every persisted aggregate needs an entity and a mapper. Three files per
  aggregate, not one.
- The team writes SQL only in migrations; queries are Spring Data derived methods
  or JPQL, and anything more complex than that is a signal to reconsider whether
  it belongs on the read side.
- The test suite needs a running Postgres, mitigated by docker-compose and by the
  skip-with-CI-enforcement arrangement in `test.definition.md` § 2.3.
- `allWarningsAsErrors` means a Spring Boot minor upgrade that deprecates
  something fails the build. That is the intended behaviour and it will
  occasionally be inconvenient.

The system trades convenience for clarity, except at persistence, where it trades
some clarity for organisational fit and pays for it with a mapper.
