# ADR 0013 – PostgreSQL Everywhere, Testcontainers for Integration Tests

## Status
Accepted (inherited from template)

## Context

ADR-0001 chose H2 for development and test, calling it "lightweight, fast, sufficient for
a reference implementation". That was true of a reference implementation.

`technical.spec.md` already carried a warning about it: Flyway's compatibility with H2 lags
behind H2 releases and can require pinning versions. That warning is a symptom — the schema
tooling and the test database were drifting apart, and the code under test was not the code
that would run.

## Decision

- **PostgreSQL is the only database.** H2 is removed entirely.
- Local development runs PostgreSQL from `docker-compose/docker-compose.yaml`.
- Integration tests start their **own** container through Testcontainers, pinned to an
  explicit image tag. They never use the developer's running stack.
- The template fixes the *rule*, not the engine: **integration tests run against the same
  engine as production.** `technical.spec.md` is a project profile, so a service on MySQL
  applies the same rule with a MySQL container.
- Gradle gains a second test task: `test` excludes `*IT` and needs nothing installed;
  `integrationTest` runs the `*IT` classes and needs Docker. `check` depends on both.

## Rationale

**Why not H2.** Its dialect and PostgreSQL's diverge. A Flyway migration that applies on H2
can fail on PostgreSQL; JPA behaves differently over key generation, types and nulls in
unique indexes. A test suite that proves a migration works on H2 has proved something about
H2. For a *reference implementation* demonstrating a method that is an acceptable trade;
for a template that real services start from, it ships a false green.

**Why the tests bring their own container.** Otherwise a test run depends on what the
developer happened to have running, and state leaks between runs. This repository has
already been bitten by that class of bug in a smaller form: the controller tests once
booted the full application without the `test` profile and wrote a *file-based* H2 database
under `app/data/`, leaking state between runs. `test.definition.md` gained a rule from it —
"no test may create a file-based database".

**Why the second Gradle task.** `test.definition.md` already separated fast tests from
adapter integration tests by name; the build did not. One task that needs Docker gives a
developer without Docker no fast feedback at all, and a gate that cannot be run locally is a
gate discovered in CI. Splitting them keeps both runnable and lets `check` still demand
both.

**A second thing these tests prove.** With `ddl-auto: validate`, the schema comes from the
migrations and Hibernate refuses to start if the entity mapping disagrees with it. So every
integration test is also a check that the migrations and the mapping agree — the single most
common thing to break and the least likely to be caught by a unit test.

## Consequences

- Docker is required for the full gate, locally and in CI. `*IT` runs go from milliseconds
  to seconds.
- The container image tag is pinned, for the same reason the JDK toolchain is: a floating
  tag makes the test depend on when it ran.
- The container is `companion object` state on a shared base class, so one database serves
  the whole run rather than one per test class.
