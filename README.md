# Contract Management Service

The backend for JRL's leasing contract management: master leasing contracts
(*Leasingrahmenvertrag*, LRV) agreed with employers, and the individual leases
(*Einzelleasingvertrag*, ELV) issued under them.

Built spec-first, structured as Ports & Adapters, and — the part that actually matters —
with its rules **enforced by the build** rather than described in prose.

Start with [`documentation/project.definition.md`](documentation/project.definition.md) for
what the service is for and what it deliberately is not.

---

## Status

Early. The domain packages do not exist yet; the first use case is the creation of a master
contract. What does exist is the process, the gates, and a tour-booking example inherited
from the template this repository grew out of.

**That example is not a reference for this domain.** It is the only code the 27 ArchUnit
rules and the documentation gates currently have to check, so it stays until the first
Contract Management context is complete end to end, and then leaves in one commit.

---

## Stack

Kotlin 2.3 · JVM 17 · Spring Boot 4 · Gradle 8.14 · PostgreSQL + Flyway · GraphQL ·
Spring Data JPA · Testcontainers · ktlint + detekt + ArchUnit.

The shape follows the platform's existing service, `risk-management-service`, which is where
the stack comes from. What was **not** taken from it is its architecture: that service is
layered per context with `@Entity` in its domain packages, and the rules here exist to
prevent exactly that
([`adr/0011`](documentation/adr/0011-persistence-annotations-stay-out-of-the-domain.adr.md)).

GraphQL is the only transport. Every frontend on the platform speaks Apollo; REST arrives
with the first machine consumer that needs it
([`adr/0020`](documentation/adr/0020-graphql-as-the-only-transport.adr.md)).

---

## Running it

```shell
docker compose -f docker-compose/docker-compose.yaml up -d   # PostgreSQL
./gradlew bootRun
```

- GraphQL at `/graphql`, with GraphiQL at `/graphiql` in development
- Executable requests for every documented status live in [`api/`](api/)

## Testing it

```shell
./gradlew test              # fast: domain, use case and slice tests. No Docker needed
./gradlew integrationTest   # every *IT, against real PostgreSQL. Needs Docker
./gradlew check             # everything, plus ktlint and detekt
```

The split is deliberate: one task that needs Docker would leave a developer without Docker
no fast feedback at all, and a gate that cannot be run locally is a gate discovered in CI.

`check` is the gate, not `detekt` — the latter is a convenience task, while `check` depends
on `detektMain` and `detektTest`, the type-resolution variants that find what it does not.

---

## How work is done here

Two nested loops. The inner loop implements one increment; the outer loop repeats it until a
use case's Definition of Done is met.

```
/spec-create <page|ticket>  → use-case spec(s), decomposition confirmed first
/uc-to-plan <ucNN>          → plan.md
/plan-to-task               → tasks.md
/execute-task <N.M>         → one task block, TDD-first
/loop-uc <UCNN>             → outer loop until the DoD is met
/code-review                → four axes, split authority
```

Non-negotiable: **no implementation without a spec, no architectural change without an ADR,
no production code without a failing test.**

Specifications are derived from the `JCM` Confluence space. They are written in English
against German sources, so a disputed term is settled by going back to the page rather than
by re-reading the spec.

---

## The idea the repository is built on

**A rule with no executable owner does not survive contact with a refactor.**

This is not a slogan; it is the repository's own history. Before the gates existed, the
coding style described a package layout no code had ever used, required fields to be
`private final` while every aggregate violated it, and forbade returning null while four
shipped, reviewed classes relied on an unwritten exception.

The worst case was the quietest. The specs' Definition of Done cites tests by name, which is
what makes it checkable. Porting the example between languages renamed nearly every test
method, and **66 of 69 citations across four specs went stale in a single commit** while
every box stayed ticked and every other gate stayed green. The specs read as complete. They
were describing tests that did not exist.

So every class of rule names its enforcer
([`coding-style.definition.md` § 9](documentation/coding-style.definition.md)), and three of
the tests read the documentation rather than the code:

- `ContextRegistryTest` parses the bounded-context registry out of
  `architecture.definition.md` § 11, in both directions — a package without a row and a row
  without a package both fail the build
- `SpecCitationsTest` checks that every `SomeTest.some_method` cited anywhere in
  `documentation/` names a test that exists
- `DocumentationLinksTest` checks that every ADR reference resolves

Where a rule genuinely cannot be automated, the last row of that table says so. That
admission is the point: when a rule in it rots, the fix is to move it up a row, not to
restate it more firmly ([`adr/0014`](documentation/adr/0014-quality-gates-are-executable.adr.md)).

---

## Documentation

Start with [`CLAUDE.md`](CLAUDE.md) — it carries the canonical authority order for every
document, and the three lists that must exist in exactly one place.

| Read this | For |
|-----------|-----|
| [`project.definition.md`](documentation/project.definition.md) | what the service is for, and its honest non-goals |
| [`architecture.definition.md`](documentation/architecture.definition.md) | package ontology, dependency rules, the context registry |
| [`technical.spec.md`](documentation/technical.spec.md) | stack, persistence, migrations, gate commands |
| [`execution.playbook.md`](documentation/execution.playbook.md) | the inner loop, per increment |
| [`loop.playbook.md`](documentation/loop.playbook.md) | the outer loop, per use case |
| [`test.definition.md`](documentation/test.definition.md) | test taxonomy and the canonical quality gates |
| [`adr/README.md`](documentation/adr/README.md) | every decision, and which tier it belongs to |
| [`notes.md`](documentation/notes.md) | live debts and open questions |

---

## Provenance

The method this repository uses — Spec-Driven Development, the two-loop process, the
document ontology and the review agents — comes from **Alpine Booking**, a Java reference
implementation written by **Dominik Galler**. The Kotlin port, the gates that parse
documentation, and the decisions in `adr/0009` onward are not his work.

The original carries no licence file, which by default means all rights reserved. This copy
exists for personal reuse and is not distributed. Going further than that — publishing it,
or handing the repository to a client — means asking the author first. That obligation is
recorded as a tripwire in [`notes.md`](documentation/notes.md), because it activates on a
specific event rather than on a date.
