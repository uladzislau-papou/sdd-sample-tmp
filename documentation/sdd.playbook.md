# SDD Process Requirements

## Purpose

This document defines how Spec-Driven Development (SDD) operates in
JRL Contract Management.

SDD is not documentation overhead.
It is an enforcement mechanism.

No implementation exists without specification.
No architectural change exists without explicit decision recording.


# 1. Spec Hierarchy (Source of Truth)

The ranked authority order lives in `CLAUDE.md`.
See `file-usage.definition.md` for what each file is responsible for.

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
- GraphQL operation must map to a Use Case.
- Use Case must reference affected Aggregates.
- Domain invariants must be test-covered.
- Outbound calls must go through Ports.
- Adapters must not contain business logic.

Tests must reflect domain behavior, not framework wiring.


# 4. Acceptance Criteria and Definition of Done

## 4.1 Acceptance Criteria

Every implementation must define measurable acceptance criteria.

Each criterion is **identified** so that tests and DoD items can cite it:

```none
**AC-01 – <short name>**
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

**A criterion about money or dates states the expected value.** "Then the monthly
rate is calculated" is not checkable; "Then `ratePerMonth` is `152.5000 EUR`" is.
This domain's defects are arithmetic, and a criterion that does not commit to a
number cannot catch one (`test.definition.md` § 2.1).

## 4.2 Definition of Done

Acceptance Criteria say *what correct behaviour is*. The Definition of Done says
*when the work stops*. Both live in the Use Case Spec — AC in § 7, DoD in § 10.

The DoD is the machine-readable exit condition of the outer loop
(`loop.playbook.md`). It is therefore subject to two rules:

- **Every `AC-NN` must be cited by at least one DoD item.** An acceptance
  criterion nothing is accountable for is decoration.
- **A DoD item must be objectively checkable** — a passing named test, an
  existing file, a `PASS` verdict, a green gate. "Code is clean" is not a DoD item.

`tasks.md` mirrors the DoD as a working scoreboard. The spec is authoritative;
where the two disagree, the spec wins and the mirror is rebuilt.


# 5. Quality Gates (Merge Blockers)

**Canonical list: `test.definition.md` § 7.** It is not restated here.

This playbook defines only the *principle*: quality gates are merge blockers,
not advisories. A change that fails any gate is incomplete, regardless of how
much of it works. There is no partial credit and no "fix it in a follow-up".

The outer loop in `loop.playbook.md` uses that same list as one of its three
exit conditions, which is why it must exist in exactly one place.


# 6. ADR Requirement

**Canonical list. This is the only ADR-trigger list in the project.**

An ADR is mandatory when:

1. Changing architectural layering or dependency rules
2. Introducing a new external dependency or library
3. Introducing new infrastructure
4. Changing persistence technology or strategy
5. Modifying transaction boundaries
6. Introducing async / event-driven processing
7. Adding messaging or an event broker (Kafka, RabbitMQ, …)
8. Modifying package ontology or domain boundaries
9. **Introducing a new bounded context** — see `architecture.definition.md` § 11;
   `adr/0003-separate-individual-leasing-context.adr.md` is the precedent for how
   one is legitimately introduced
10. Changing the cross-context interaction model — in particular, moving an
    interaction between the domain-event form and the shared-transaction form
    (`architecture.definition.md` § 10)
11. Introducing caching or another cross-cutting concern
12. Changing the API contract in a breaking way — for GraphQL that means removing a
    field or an operation, renaming one, making a nullable field non-nullable, or
    removing an enum value. Adding an optional field is not a trigger
13. Raising the JVM, Kotlin or framework baseline

Architectural decisions must never be implicit.

Implementation **waits** for ADR confirmation. An agent that hits a trigger
mid-increment halts and asks; it does not decide.

## 6.1 Things that look like triggers and are not

Recorded so the list is usable rather than paralysing:

- **Adding a Flyway migration** is not item 4. Changing *how* the schema is owned
  would be; adding a column the way every other column was added is not.
- **Adding a value object, a domain event, or a use case** is not item 8. The
  ontology is the package structure, not its contents.
- **Adding an optional field to a GraphQL type** is not item 12 — it is backwards
  compatible by construction, which is most of the point of the schema.
- **Adding a test dependency** *is* item 2. The scope of a dependency does not
  change whether it is one.


# 7. Scope Control

To prevent architectural drift:
- No implementation without Spec
- No refactoring outside declared scope
- No cross-context leakage
- **No new bounded context** without an ADR and a new row in
  `architecture.definition.md` § 11
- No direct repository access from a GraphQL controller
- No framework annotations inside the domain layer

Every change must remain within its declared bounded context.

Scope control is **enforced, not merely stated**: the `ddd-hex-reviewer` subagent
checks these rules against the working diff on every increment and returns `PASS`
or `DRIFT`, and the ArchUnit suite checks the mechanical subset on every build. A
`DRIFT` verdict blocks the increment. Drift is never traded away for progress — see
`loop.playbook.md`.


# 8. Domain Governance Rules

The following are strict:
- Aggregates must be Always-Valid
- Invariants enforced inside `init` blocks or state transition functions
- No setter-based mutation; a `var` in an aggregate is `private set`
- Domain Events emitted explicitly
- Domain layer contains no Spring annotations and **no `jakarta.persistence` annotations**
- Persistence is an adapter concern, and the JPA entity is a different class from the
  aggregate (`architecture.definition.md` § 4.6)

Violations invalidate the change.


# 9. Non-Goals

JRL Contract Management does not aim to:
- Demonstrate framework tricks
- Maximize feature count
- Optimize prematurely
- Blur architectural boundaries

It exists to demonstrate disciplined, domain-centric backend architecture.
