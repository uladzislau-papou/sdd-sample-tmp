# Domain Specification – Master

## Purpose

Describes the `Master` aggregate: the party this service holds contracts with, together with
the `Contract` entities inside it.

This is the **single source for the invariants**. The six use-case specifications reference
them; none of them restates one. A rule copied into a use-case spec is a rule with two places
to drift, and this repository has already paid for that mistake twice
(`HANDOFF.md` § "a summary is not a source").

**Every rule here is invented.** `project.definition.md` says why the domain is invented, and
the honest consequence is that no rule below can be defended by appeal to a source. They are
chosen so that an Always-Valid aggregate has something to enforce and a test has something to
assert.


## 1. Aggregate Root

**Name:** `Master`

**Description:** A party the business holds contracts with. It owns zero or more `Contract`
entities, which have no existence outside it: they are created, changed and removed through
the root, and loaded and saved with it.

`Contract` is an **entity, not a Value Object** — two contracts with identical fields are
different contracts, and a contract keeps its identity when its period changes.

`Contract` is **not an aggregate root**. There is exactly one repository outport,
`MasterRepository`, and adding a `ContractRepository` is drift.
`adr/0024-one-context-with-master-as-the-aggregate-root.adr.md` carries the argument.

### Fields

| Field | Type | Notes |
|-------|------|-------|
| `id` | `MasterId` | UUID, assigned at creation |
| `name` | `MasterName` | Value Object |
| `customerNumber` | `CustomerNumber` | Value Object. **Not** globally unique — see § 2 |
| `status` | `MasterStatus` | `ACTIVE` or `INACTIVE` |
| `createdAt` | `Instant` | Supplied by the driver from `ClockPort`, never read in the domain |
| `contracts` | `List<Contract>` | Exposed as an unmodifiable view |

`Contract` fields:

| Field | Type | Notes |
|-------|------|-------|
| `id` | `ContractId` | UUID, assigned when the contract is added |
| `contractNumber` | `ContractNumber` | Value Object. Unique **within its Master** |
| `period` | `ContractPeriod` | Value Object holding `startDate` and `endDate` |
| `monthlyAmount` | `Money` | Value Object holding an amount and a `CurrencyCode` |


## 2. Invariants (Always-Valid)

Violations MUST result in an exception. Each row names the type that owns the rule, because
the rule lives in exactly one place and the aggregate factory therefore performs no validation
of its own.

### Value-object invariants

| # | Invariant | Owned by | Exception |
|---|-----------|----------|-----------|
| I-01 | `name` is non-blank after trimming and at most 200 characters. Trimming qualifies only the blank check — the stored value is the string as supplied, not the trimmed form, pinned by `MasterNameTest.nameIsKeptAsGiven`. The 200-character bound is measured on the **raw** value, not the trimmed one, so padding counts toward the limit, pinned by `MasterNameTest.nameLongerThanTheLimitOnlyBeforeTrimmingThrows` | `MasterName` | `InvalidMasterException` |
| I-02 | `customerNumber` matches `^[A-Z]{2}-[0-9]{6}$` | `CustomerNumber` | `InvalidMasterException` |
| I-03 | `contractNumber` is non-blank after trimming and at most 50 characters | `ContractNumber` | `InvalidContractException` |
| I-04 | `endDate` is strictly after `startDate` | `ContractPeriod` | `InvalidContractException` |
| I-05 | `monthlyAmount` is strictly positive and has at most 2 decimal places | `Money` | `InvalidContractException` |
| I-06 | `currency` is three uppercase letters (ISO 4217 shape, not membership) | `CurrencyCode` | `InvalidContractException` |

I-06 checks the **shape** and not the code list deliberately: validating membership needs a
currency table, a table needs a source of truth, and inventing one would be inventing a
dependency. A shape rule is honest about what it knows.

### Aggregate-level invariants

These are the three that cannot be enforced by any single Value Object or by a `Contract`
looking at itself, and they are the reason the aggregate boundary is where it is.

| # | Invariant | Exception |
|---|-----------|-----------|
| I-07 | A `contractNumber` appears at most once within one `Master`. Two different Masters may each hold `C-0001` | `DuplicateContractNumberException` |
| I-08 | A `Master` holds at most **50** contracts | `ContractLimitExceededException` |
| I-09 | A contract may be added only while the `Master` is `ACTIVE` | `MasterNotActiveException` |

**`customerNumber` is deliberately not unique across Masters.** The service has no optimistic
locking (`project.definition.md`, Non-Goals), so two concurrent creates would both read "no
such customer number" and both succeed. A constraint the system cannot actually hold is worse
than no constraint, because tests and readers both come to rely on it. I-07 is enforceable
precisely because it lives inside one aggregate, loaded and saved as a unit.


## 3. State Model

Possible states:

- `ACTIVE`
- `INACTIVE`

A Master is `ACTIVE` when created.

Allowed transitions:

- `ACTIVE` → `INACTIVE` (`deactivate`)
- `INACTIVE` → `ACTIVE` (`activate`)

Idempotent, not illegal:

- `ACTIVE` → `ACTIVE` and `INACTIVE` → `INACTIVE` are **no-ops**. They emit no event and throw
  nothing. UC03 sets a Master's status to a requested value rather than commanding a
  transition, so a caller resubmitting an unchanged form is not an error.

There are no illegal transitions. This is stated rather than left blank because a two-state
model with both directions open is easy to mistake for an incomplete one.

**Deletion is not a state.** There is no `DELETED`; `status` says whether a Master is trading,
not whether it exists (`project.definition.md`, Non-Goals: no soft delete). `Contract` has no
state model at all.


## 4. Behavior

All state changes go through named methods on the root. There are no setters, and the class is
not a `data class` — a generated `copy()` would hand every caller a way around these methods.

### `Master.create(id, name, customerNumber, now): Master`

- **Preconditions:** none beyond the value objects' own (I-01, I-02), which have already run
  by the time this is called.
- **Postconditions:** `status` is `ACTIVE`; `contracts` is empty; `createdAt` is `now`.
- **Emits:** `MasterCreated`.

`now` is supplied by the driver from `ClockPort`. The domain never calls `Instant.now()`, and
`ClassRoleRulesTest.domainNeverReadsTheClockDirectly` enforces it.

### `Master.rename(name)`

- **Preconditions:** none beyond I-01.
- **Postconditions:** `name` is the new value.
- **Emits:** `MasterRenamed`, and **only if the name actually changed**.

### `Master.activate()` / `Master.deactivate()`

- **Preconditions:** none.
- **Postconditions:** `status` is `ACTIVE` / `INACTIVE`.
- **Emits:** `MasterActivated` / `MasterDeactivated`, and only if the status actually changed
  (§ 3).

### `Master.addContract(contractId, contractNumber, period, monthlyAmount): Contract`

- **Preconditions:** I-09 (`ACTIVE`), I-07 (number unused in this Master), I-08 (fewer than 50
  contracts). Checked **in that order**, so a caller fixing one error at a time meets the most
  fundamental first.
- **Postconditions:** the contract is in `contracts`.
- **Emits:** `ContractAddedToMaster`.

### `Master.removeContract(contractId)`

- **Preconditions:** a contract with that id is in this Master, else `ContractNotFoundException`.
- **Postconditions:** it is no longer in `contracts`.
- **Emits:** `ContractRemovedFromMaster`.

Removing is permitted while `INACTIVE`. Only *adding* is gated by I-09 — a Master that has
stopped trading must still be able to shed contracts, and a rule that traps data in a
deactivated record is a rule that gets worked around.

### `Master.reconstitute(...)`

Rebuilds a Master from persisted state with no pending events, re-checking no invariants. It
may be called **only** from `..outbound.persistence..`, which
`ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence` enforces. A valid past fact is
reloaded, not re-validated; re-running I-07 here would make a rule change retroactively
un-loadable.

### `Master.pullDomainEvents(): List<DomainEvent>`

Returns and clears the recorded events. A second call returns empty. The snapshot is a copy.


## 5. Domain Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `MasterCreated` | `create` | `masterId`, `occurredAt` |
| `MasterRenamed` | `rename`, when the name changed | `masterId`, `occurredAt` |
| `MasterActivated` | `activate`, when the status changed | `masterId`, `occurredAt` |
| `MasterDeactivated` | `deactivate`, when the status changed | `masterId`, `occurredAt` |
| `ContractAddedToMaster` | `addContract` | `masterId`, `contractId`, `occurredAt` |
| `ContractRemovedFromMaster` | `removeContract` | `masterId`, `contractId`, `occurredAt` |
| `MasterDeleted` | UC04 — see below | `masterId`, `occurredAt` |

`MasterDeleted` is the odd one. Deletion removes the aggregate rather than changing it, so
there is no post-deletion object to pull events from. The **driver** records it, from the
aggregate it loaded, before calling `MasterRepository.delete`. That is an exception to "events
come from the aggregate", it is the only one, and UC04 § 6 states it again at the point of
use so that nobody implementing UC04 has to have read this file.

Nothing consumes any of these events (`project.definition.md`, Non-Goals). They are published
through `DomainEventPublisher` inside the driver's transaction (`adr/0002`) and logged.


## 6. Failure Scenarios

| Exception | Raised when |
|-----------|-------------|
| `InvalidMasterException` | I-01 or I-02 violated |
| `InvalidContractException` | I-03, I-04, I-05 or I-06 violated |
| `MasterNotFoundException` | No Master with the given id (raised by the driver after the repository returns nothing) |
| `DuplicateContractNumberException` | I-07 violated |
| `ContractLimitExceededException` | I-08 violated |
| `MasterNotActiveException` | I-09 violated |
| `ContractNotFoundException` | `removeContract` names a contract this Master does not hold |

All of them live in `contract.core.domain.master.exception` and extend nothing framework-bound.
Their mapping to GraphQL error classifications is each use case's § 9, not this document's —
the domain does not know it is reached over GraphQL.


## 7. Test Requirements

Expressed as Definition of Done items in the use-case specs, where each names the test that
satisfies it (`test.definition.md` § 9). What must be covered:

- **Invariant tests** — one per row in § 2, on the type that owns it, asserting the specific
  exception. I-07, I-08 and I-09 are asserted on `MasterTest`, not on a value object.
- **Transition tests** — `activate` / `deactivate` in both directions, plus the two no-op
  cases asserting **no event was recorded**.
- **Event emission tests** — one per row in § 5, including the two conditional emissions:
  renaming to the same name and activating an already-active Master each emit nothing.
- **Failure tests** — one per row in § 6.
- **Ordering test** — a call violating I-07, I-08 and I-09 at once raises
  `MasterNotActiveException`, pinning the order § 4 specifies.
- **Reconstitution test** — a Master rebuilt from state that would fail I-07 today loads
  without error and has no pending events.
