# Spec-Driven Service Template

A template for starting Kotlin backend services that are built spec-first, structured as
Ports & Adapters, and — the part that actually matters — whose rules are **enforced by the
build** rather than described in prose.

It ships as a working service, not as a skeleton. The tour-booking example inside it is
what proves the template compiles, its gates fire, and its documents describe something
real.

---

## What is in here

| | |
|---|---|
| **The process** | Two nested loops: an inner loop per increment, an outer loop per use case. Domain-independent, reusable as-is |
| **The gates** | 27 ArchUnit rules, plus tests that read the documentation itself. A rule that rots fails a build |
| **The agents** | Read-only reviewers and a documenter, dispatched at defined points in the loop |
| **The example** | Four use cases across two bounded contexts, one of them reached over both REST and GraphQL |

Stack: Kotlin 2.3 · JVM 17 · Spring Boot 4 · Gradle 8.14 · PostgreSQL + Flyway · REST +
GraphQL · Spring Data JPA · Testcontainers · ktlint + detekt + ArchUnit.

---

## Starting a service from it

```shell
# 1. Take a copy, then give it an identity
./gradlew initService -PserviceName=billing -PserviceGroup=com.acme

# 2. Bring up the database and run everything
docker compose -f docker-compose/docker-compose.yaml up -d
./gradlew build
```

`initService` moves the package directories, rewrites the package declarations, sets
`rootProject.name` and `group`, and replaces `documentation/project.definition.md` with the
blank in `project.definition.template.md`.

Then, before writing any code:

1. **Fill in `documentation/project.definition.md`.** It is the highest-ranked document in
   the authority order, so every agent run reads it first. Its Non-Goals section earns its
   keep — an absence that is not written down gets invented, differently each time.
2. **Decide what happens to the example.** Delete `booking/` and `guide/` and their specs,
   or keep one slice as a reference while you write your first real one. Deleting a context
   means deleting its row from `architecture.definition.md` § 11 — the registry is parsed,
   so the two cannot disagree.
3. **Review the profile documents.** `architecture.definition.md`,
   `coding-style.definition.md` and `technical.spec.md` describe *this* project. You own
   them. Very little of the coding style is domain-specific; the architecture registry is
   entirely yours.

---

## Running it

```shell
docker compose -f docker-compose/docker-compose.yaml up -d   # PostgreSQL
./gradlew bootRun
```

- REST: `POST /api/v1/bookings`, `POST /api/v1/bookings/{id}/confirm`,
  `POST /api/v1/guide-tours/{id}/start`
- GraphQL: `/graphql`, with GraphiQL at `/graphiql` in development
- Executable requests for every documented status live in [`api/`](api/)

## Testing it

```shell
./gradlew test              # fast: domain, use case and slice tests. No Docker needed
./gradlew integrationTest   # every *IT, against real PostgreSQL. Needs Docker
./gradlew build             # everything, plus ktlint and detekt
```

The split is deliberate: one task that needs Docker would leave a developer without Docker
no fast feedback at all, and a gate that cannot be run locally is a gate discovered in CI.

---

## The idea worth stealing, if you take nothing else

**A rule with no executable owner does not survive contact with a refactor.**

This is not a slogan; it is the repository's own history. Before the gates existed, the
coding style described a package layout no code had ever used, required fields to be
`private final` while every aggregate violated it, and forbade returning null while four
shipped, reviewed classes relied on an unwritten exception. The bounded-context registry
lived as prose in one file and as a string literal in a test, so adding a context meant
editing both — and the test would have kept passing against the stale list.

The worst case was the quietest. The specs' Definition of Done cites tests by name, which
is what makes it checkable. Porting the example from Java to Kotlin renamed nearly every
test method, and **66 of 69 citations across four specs went stale in a single commit**
while every box stayed ticked and every other gate stayed green. The specs read as
complete. They were describing tests that did not exist.

So every class of rule here names its enforcer
([`coding-style.definition.md` § 9](documentation/coding-style.definition.md)), and two of
the tests read the documentation rather than the code:

- `ContextRegistryTest` parses the context registry out of
  `architecture.definition.md` § 11 — so the document is the single source, and a
  disagreement with the packages on disk is a build failure
- `SpecCitationsTest` checks that every `SomeTest.some_method` cited anywhere in
  `documentation/` names a test that exists. It found four genuine coverage gaps on its
  first run

Where a rule genuinely cannot be automated, the last row of that table says so. That
admission is the point: when a rule in it rots, the fix is to move it up a row, not to
restate it more firmly.

---

## Documentation

Start with [`CLAUDE.md`](CLAUDE.md) — it carries the canonical authority order for every
document, and the two lists that must exist in exactly one place.

| Read this | For |
|-----------|-----|
| [`project.definition.md`](documentation/project.definition.md) | what the example is, and its honest limitations |
| [`architecture.definition.md`](documentation/architecture.definition.md) | package ontology, dependency rules, the context registry |
| [`execution.playbook.md`](documentation/execution.playbook.md) | the inner loop, per increment |
| [`loop.playbook.md`](documentation/loop.playbook.md) | the outer loop, per use case |
| [`test.definition.md`](documentation/test.definition.md) | test taxonomy and the canonical quality gates |
| [`adr/README.md`](documentation/adr/README.md) | which decisions you inherit, and which belong to the example |

---

## Provenance

This template was extracted from **Alpine Booking**, a Java reference implementation of
Spec-Driven Development written by **Dominik Galler**. The method, the two-loop process, the
document ontology and the review agents are his work; the Kotlin port, the dual-transport
example, the parsing gates and the rebuilt ADR set are not.

The original carries no licence file, which by default means all rights reserved. This copy
exists for personal reuse and is not distributed. Anyone intending to go further than that
should ask the author first.
