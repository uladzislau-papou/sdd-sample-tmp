# Domain Specification – <EntityName>

<!--
File name: entity-<kebab-name>.spec.md (`file-naming.definition.md`).

RMS domain objects are JPA entities (`modelling.definition.md` § 1). This template
describes a persistent entity and its lifecycle, not a framework-free aggregate.
-->

## Status
SPECIFIED | IMPLEMENTED | SUPERSEDED

## Module
`<qes | kyc | onb | boni | common>`

## Class
`com.jobradleasing.riskmanagementservice.<module>.domain.model.<EntityName>`

## Table
`<table_name>` — created by `<VYYYYMMDDHHmm__create_….sql>`


## 1. Purpose

One paragraph: what this row represents in the business, and what owns its lifecycle.


## 2. Columns

| Property | Column | Type | Nullable | Meaning (and what `null` means) | Written by |
|----------|--------|------|----------|--------------------------------|------------|
| `id` | `id` | UUID | no | Primary key | JPA |
| | | | | | |

Rules to state explicitly:

- Which properties are `val` (immutable after insert) and which are `var`
- Which service owns writes to each lifecycle column (`modelling.definition.md` § 2.2)
- Whether `@Version` optimistic locking is present, and why
- Which columns hold personal data, and how they are protected


## 3. Relations

| Relation | Target | Fetch | Cascade | Why |
|----------|--------|-------|---------|-----|
| | | LAZY | | |

State whether this entity is a **consistency anchor** (`modelling.definition.md` § 2.4)
and, if so, which child rows are written only through its owning service.


## 4. Lifecycle

Applies only to entities with a status column. Otherwise:
`Not applicable — no lifecycle status.`

States:

- `STATE_A` — meaning, what is true while in it
- `STATE_B`
- `STATE_C` — terminal

Legal transitions:

| From | Event | To | Triggered by | Guard |
|------|-------|----|--------------|-------|
| | | | | |

Illegal transitions and what they produce:

| Attempt | Result |
|---------|--------|
| `C → A` | `BadUserInputException` naming the case, event and current status |

Where legality is declared: `<kyc/statemachine/… | the owning service>`
(`modelling.definition.md` § 2.6).


## 5. Behaviour on the entity

Methods that live on the entity because they use only its own fields:

| Method | Returns | Meaning |
|--------|---------|---------|
| | | |

`Not applicable — this entity carries state only.` is a valid and common answer.

Explicitly **not** on the entity, and where it lives instead:

| Logic | Lives in | Why |
|-------|----------|-----|
| | | it needs another row / the clock / configuration |


## 6. Audit Events

| Event | Emitted when | Actor | Catalogue entry |
|-------|-------------|-------|-----------------|
| | | operator / `svc:risk-management-service` | `docs/…` |

`Not applicable — no regulated action touches this entity.`


## 7. Concurrency

- Is `@Version` present? Which concurrent paths make it necessary?
- What does a lock conflict look like to the caller?
- Are there uniqueness constraints the database enforces, and what error do they produce?


## 8. Failure Scenarios

- Illegal transition
- Constraint violation (uniqueness, FK, NOT NULL)
- Optimistic-lock conflict
- Rehydration of a row that no longer satisfies a current rule
  (`modelling.definition.md` § 3.2) — how is it handled?


## 9. Test Requirements

Expressed as DoD items in the use case specs that touch this entity. This section lists
what those items must cover:

- Every legal transition
- Every illegal transition the contract names
- Every constraint the database enforces (integration test)
- Optimistic-lock conflict behaviour, if `@Version` is present
- Audit event emission and attribution
- Round-trip of any JSON or converted column
