# SDD Process Requirements

## Purpose

How Spec-Driven Development operates in Risk Management Service.

SDD is not documentation overhead. It is an enforcement mechanism.

No implementation without specification. No architectural change without explicit decision
recording.

---

# 1. Spec Hierarchy

The ranked authority order lives in `CLAUDE.md`. See `file-usage.definition.md` for what
each file is responsible for.

Implementation must always reference at least one use case spec. No orphan code.

---

# 2. Mandatory Specification Types

| Spec | Required when |
|------|---------------|
| Use case spec | Always — any behaviour change |
| Domain spec | A new entity, or a change to an existing entity's lifecycle, columns or invariants |
| Integration spec | A new or changed inbound surface (GraphQL operation, REST endpoint, webhook) or outbound integration (provider client, outbox delivery) |
| ADR | Any § 6 trigger fires |
| Audit catalogue entry (`docs/`) | A new or changed audit event |

If a change cannot be mapped to one of these, it must not be implemented.

---

# 3. Traceability Model

```
Use Case → Entity/Entities → Transition rules → Audit events →
Inbound surface → Outbound integrations → Tests
```

Every:

- GraphQL operation and REST endpoint maps to a use case
- Use case names the entities it reads and the entities it writes
- Transition rule is test-covered
- Outbound provider call goes through an `api/integration` client
- Regulated action emits a catalogued audit event
- Externally reachable surface declares its required scope

Tests reflect business behaviour, not framework wiring.

---

# 4. Acceptance Criteria and Definition of Done

## 4.1 Acceptance Criteria

Every implementation defines measurable acceptance criteria, each with a stable
identifier so tests and DoD items can cite it:

```none
**AC-01 – <short name>**
Given
When
Then
```

Criteria must describe:

- Business behaviour and outcomes
- Lifecycle transitions and the states they are legal from
- Failure scenarios and the error classification each produces
- Authorization requirements
- Audit consequences

Technical implementation details are not acceptance criteria.

## 4.2 Definition of Done

Acceptance Criteria say *what correct behaviour is*. The Definition of Done says *when the
work stops*. Both live in the use case spec — AC in § 7, DoD in § 10.

The DoD is the machine-readable exit condition of the outer loop (`loop.playbook.md`), so:

- **Every `AC-NN` must be cited by at least one DoD item.** An acceptance criterion nothing
  is accountable for is decoration.
- **A DoD item must be objectively checkable** — a passing named test, an existing file, a
  `PASS` verdict, a green gate. "Code is clean" is not a DoD item.

`tasks.md` mirrors the DoD as a working scoreboard. The spec is authoritative; where the
two disagree, the spec wins and the mirror is rebuilt.

---

# 5. Quality Gates (Merge Blockers)

**Canonical list: `test.definition.md` § 7.** Not restated here. Commands:
`technical.spec.md` § 7.

This playbook defines only the *principle*: quality gates are merge blockers, not
advisories. A change that fails any gate is incomplete, regardless of how much of it works.
There is no partial credit and no "fix it in a follow-up".

---

# 6. ADR Requirement

**Canonical list. This is the only ADR-trigger list in the project.**

An ADR is mandatory when:

1. **Changing architectural layering or dependency rules** — including introducing
   `inport`/`outport` packages into any module (`architecture.definition.md` § 2.2)
2. **Introducing a new external dependency or library** — anything added to
   `build.gradle.kts` or `package.json`
3. **Introducing new infrastructure** — a queue, a cache, a second datastore, a new bucket,
   a new scheduled-job substrate
4. **Changing persistence technology or strategy** — leaving JPA, adding a second ORM,
   changing the migration convention, adding `baseline-on-migrate`
5. **Modifying transaction boundaries** — widening one across modules, changing propagation,
   or moving where a transaction opens
6. **Introducing async or event-driven processing** where a synchronous call exists today
7. **Adding messaging or an event broker** (Kafka, SQS, RabbitMQ, …)
8. **Modifying package ontology or module boundaries** — including moving a concept between
   modules
9. **Introducing a new top-level module** — see `architecture.definition.md` § 11.1
10. **Changing the cross-module interaction model** — inverting a registered edge in
    § 11.2, or replacing a direct call with an event (or vice versa)
11. **Introducing caching or another cross-cutting concern**
12. **Changing API versioning strategy** — the `/v1/` in the endpoint paths, or a breaking
    GraphQL schema change
13. **Raising the Kotlin, JVM, Spring Boot or Gradle baseline**
14. **Onboarding a new external provider**, or changing which provider is the default for
    an operation. A provider is a contract, an SLA and a failure mode, not a config value
15. **Changing the authentication or authorization model** — the JWT scheme, the scope
    taxonomy, or how operator identity is established
16. **Changing the audit contract** — the CloudEvents envelope, the transport, or the
    guarantee. *Adding* an event to the existing contract is a catalogue update, not an ADR
17. **Changing how personal data is stored, hashed, retained or deleted**

Triggers 14–17 are RMS-specific and exist because those decisions are the ones this service
cannot quietly reverse.

Architectural decisions must never be implicit.

Implementation **waits** for ADR confirmation. An agent that hits a trigger mid-increment
halts and asks; it does not decide.

## 6.1 ADR format

`documentation/adr/<nnnn>-<kebab-title>.adr.md`. See `file-naming.definition.md`.

Sections: Status · Context · Decision · Consequences · Alternatives considered.

ADRs are **immutable once accepted**. Superseding one means writing a new ADR and marking
the old one `Superseded by ADR <nnnn>`.

An ADR written after the fact to record an existing decision is marked
`Status: Accepted (retroactive)` and says so in its Context. Honesty about provenance costs
nothing and prevents a reader mistaking a reconstruction for a deliberation.

---

# 7. Scope Control

To prevent architectural drift:

- No implementation without a spec
- No refactoring outside declared scope
- No new top-level module without an ADR and a row in `architecture.definition.md` § 11.1
- No new cross-module edge without registering it in § 11.2
- No direct repository access from controllers
- No provider vocabulary outside `api/integration` and its mappers
- No new externally reachable endpoint without a declared scope and authorization tests
- No edit to an applied Flyway migration
- No new entry in the § 11.3 wart registry to legalise new code — a wart is something that
  already exists and is bounded

Every change stays within its declared module unless the spec says otherwise.

Scope control is **enforced, not merely stated**: `rms-architecture-reviewer` checks these
rules against the working diff on every increment and returns `PASS` or `DRIFT`. A `DRIFT`
verdict blocks the increment.

---

# 8. Domain Governance Rules

Strict:

- Lifecycle transition legality is declared in one place — the state machine for `kyc`, the
  owning service elsewhere
- A lifecycle column is written by exactly one service
- Entities do not perform IO, call repositories, or read the clock for decisions
- Business failures use the typed exceptions in `domain/exception`, never
  `IllegalArgumentException` or `IllegalStateException`
- A regulated action emits a catalogued audit event, attributed to `OperatorContext` or to
  the service actor
- Personal data never reaches logs, exception messages, or unhashed audit payloads
- A `when` over an enum is exhaustive without `else`

Violations invalidate the change.

---

# 9. Non-Goals

RMS's SDD process does not aim to:

- Retrofit specs to the entire existing codebase before new work can proceed. Specs are
  written for what is **touched**, and coverage grows with the work
- Convert the codebase to Ports & Adapters as a side effect of feature work
- Replace the service repository's `README.md` and `docs/` — those stay operational
- Produce specs no one reads. A spec exists to make a decision checkable
