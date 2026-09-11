# Port Specification – IndividualLeasingContractRepository (Outport)

## Purpose

Defines the persistence boundary for the `IndividualLeasingContract` aggregate on
the write side.

SDD: See `documentation/domain/aggregate-individual-leasing-contract.spec.md`


## 1. Interface

```
individualleasing.core.outport.IndividualLeasingContractRepository
```

Framework-free. No `jakarta.persistence` type, no `org.springframework.data` type,
and no `java.util.Optional` in any signature. Absence is `T?`.

The same JPA hazard as the master-contract port applies verbatim: Spring Data's
`IndividualLeasingContractJpaRepository` is a different interface in
`outbound.persistence.write`, and promoting it to the outport would put framework
types in the core and hand managed entities to it.


## 2. Method Contracts

### 2.1 save

```kotlin
fun save(contract: IndividualLeasingContract)
```

**Responsibility:** Persist a new lease atomically, including its
`IlcConfiguration`.

**Postconditions:**
- Both rows written, or neither.
- Every monetary property is persisted at scale 4 — `leasing_value`,
  `rate_per_month`, `service_rate`, `insurance_rate`, `residual_value`. A silent
  truncation to scale 2 would change the contract and is only catchable here
  (`test.definition.md` § 2.3).

**Exceptions:** a duplicate `elv_number` surfaces as `DuplicateKeyException` from
the unique constraint. **This is a real domain condition surfacing as an
infrastructure exception**, and it is a known gap — see § 6.

Used by: UC05.

### 2.2 findById

```kotlin
fun findById(contractId: IndividualLeasingContractId): IndividualLeasingContract?
```

**Responsibility:** Load one lease by identity, fully reconstituted with its
configuration.

**Postconditions:**
- `null` when no row matches. Translating absence into
  `IndividualLeasingContractNotFoundException` is the driver's job.
- No pending domain events.
- Detached (§ 3).

Used by: UC07.

### 2.3 update

```kotlin
fun update(contract: IndividualLeasingContract)
```

**Responsibility:** Persist state changes to an existing lease.

**Postconditions:**
- **Every mutable property is written**: `status`, `terminated_at`,
  `terminated_by`, `cancellation_reason`. The three termination columns are written
  as null for a live lease, so a lease that was never terminated stays
  distinguishable from one terminated without a reason.
- Immutable fields — `id`, `elv_number`, `mlc_id`, `employer_id`, `lessor_id`,
  `job_cyclist_id`, `bike_id`, `kau_number` and the whole configuration — are set
  once by `save` and must not change. A lease's terms do not change after issue;
  that is what `COPIED_ONCE` inheritance means.

> **Standing obligation, as on the master-contract side.** Four mutable properties
> today. Any new one must reach the write path *and* an `IT.update_persists*`
> assertion in the same increment, or the change will not survive a write and
> nothing will fail.

**Idempotency:** Idempotent for identical aggregate state. UC08's driver relies on
this: it skips `update` entirely when the aggregate's `MASTER_CONTRACT` no-op
produced no event.

Used by: UC06, UC07, UC08.

### 2.4 findPendingActivationByMasterContractId

```kotlin
fun findPendingActivationByMasterContractId(
    masterLeasingContractId: String,
): List<IndividualLeasingContract>
```

**Responsibility:** Return the leases under a master contract that are eligible for
activation, i.e. `PENDING_ACTIVATION`.

**Postconditions:** an empty list when none qualify, never null; each element fully
reconstituted with no pending events; no ordering guaranteed.

Used by: UC06 (`ActivateIndividualLeasingContractsDriver`).

> **Why the criterion lives here.** A `status == PENDING_ACTIVATION` filter in the
> listener or driver would be business logic in an inbound adapter
> (`architecture.definition.md` § 4.8). It is also load-bearing: the fan-out runs in
> one `REQUIRES_NEW` transaction and `activate` throws for a `TERMINATED` lease, so
> relying on the aggregate guard alone would let one ineligible lease roll back the
> entire batch. The aggregate's guard still holds independently; the query only
> selects candidates (`architecture.definition.md` § 4.6).

The parameter is a `String`, not a typed id: the identity is owned by
`masterleasing` and is opaque here (ADR 0005 category 2). This port must not import
`MasterLeasingContractId`, and `ContextRegistryTest` enforces that.

### 2.5 findTerminableByMasterContractId

```kotlin
fun findTerminableByMasterContractId(
    masterLeasingContractId: String,
): List<IndividualLeasingContract>
```

**Responsibility:** Return the leases under a master contract that may still be
terminated — every **non-terminal** state (`PENDING_ACTIVATION`, `ACTIVE`).

**Postconditions:** an empty list when none qualify, never null; fully
reconstituted; no ordering guaranteed. `TERMINATED` and `EXPIRED` leases are
excluded by the query, so UC08's fan-out never asks the aggregate to do something it
would no-op or reject.

Used by: UC08 (`TerminateContractsByMasterContractDriver`).

> **The criterion matters more here than anywhere else in the system**, and it is
> written as an **exclusion** on purpose:
>
> ```
> status NOT IN ('TERMINATED', 'EXPIRED')
> ```
>
> not `IN ('PENDING_ACTIVATION', 'ACTIVE')`. A new non-terminal status added to
> `IndividualLeasingContractStatus` is then terminable by default, which is the
> safer direction to be wrong in. Listing the terminable states would silently
> exclude it and leave those leases live after their master contract was
> cancelled — the exact failure UC04 exists to prevent.
>
> It also makes the pinning test matter more than for § 2.4: a mistake here
> **over-selects**, the aggregate's guard then throws on an ineligible lease, and
> because UC08 shares UC04's transaction the whole master-contract cancellation
> rolls back.
>
> And the criterion must live here rather than in the caller for a second reason
> § 2.4 does not have: the ultimate caller is the **other context**. If "which
> leases are affected" lived there, `masterleasing` would have to know
> `IndividualLeasingContract`'s state model, and the boundary would be gone
> (`architecture.definition.md` § 4.6).

Pinned by `IndividualLeasingContractPersistenceAdapterIT.findTerminableByMasterContractId_returnsEveryNonTerminalLease`
and `.findTerminableByMasterContractId_returnsEmpty_whenEveryLeaseIsTerminal`, plus
end to end by `CancelMasterLeasingContractIT.cancel_skipsTerminalLeases_andStillCancelsTheContract`.
A stub cannot catch a wrong column or literal in the generated SQL.


## 3. Transaction Boundary and Detachment

The transaction is owned by the calling driver, never by the repository.

Owners: `IssueIndividualLeasingContractDriver`,
`TerminateIndividualLeasingContractDriver`.

Two special cases, and they propagate **oppositely**:

| Driver | Propagation | Why |
|--------|-------------|-----|
| `ActivateIndividualLeasingContractsDriver` (UC06) | `REQUIRES_NEW` | Runs `AFTER_COMMIT` of `masterleasing`'s transaction, so there is nothing to join. Without it, each repository call would run in auto-commit and a half-failed fan-out would be unobservable (ADR 0002) |
| `TerminateContractsByMasterContractDriver` (UC08) | `REQUIRED` (default) | **Joins** UC04's transaction. `REQUIRES_NEW` here would commit independently and give the illusion of atomicity while the master contract's cancellation could still roll back (`architecture.definition.md` § 10, condition 3) |

Getting these two the wrong way round produces a system that works in every test
that does not force a rollback. `CancelMasterLeasingContractRollbackIT` is the only
test that distinguishes them.

### Detachment

**An aggregate returned by `findById` or either finder MUST be detached.** Mutating
it without calling `update` must leave the database unchanged (ADR 0009). Pinned by
`IndividualLeasingContractPersistenceAdapterIT.findById_returnsADetachedAggregate`.

This matters more for the finders than for `findById`: UC06 and UC08 iterate over a
list of loaded aggregates and call `update` per element, and if those were managed,
a lease the driver deliberately skipped could still be flushed.


## 4. Reference Implementation

`individualleasing.outbound.persistence.write.IndividualLeasingContractPersistenceAdapter`,
delegating to `IndividualLeasingContractJpaRepository` with mapping in
`IndividualLeasingContractMapper`.

Schema: `V2__DDL_create_individual_leasing_contract.sql`.

Mapping notes:

| Domain | Column | Note |
|--------|--------|------|
| `IndividualLeasingContractId` | `id uuid` | |
| `ElvNumber` | `elv_number varchar(16)`, **unique** | format enforced by the value object; the constraint is a backstop |
| `masterLeasingContractId` (`String`) | `mlc_id uuid` | **no FK constraint** — a foreign key here would be a compile-time-invisible coupling to the other context's table, and the boundary is supposed to be logical, not enforced by the database |
| `JobCyclistId`, `BikeId`, `KauNumber` | `varchar(64)` | |
| `IndividualLeasingContractStatus`, `TerminatedBy` | `varchar(24)` via `name()` | `@Enumerated(EnumType.STRING)`, never ordinal |
| `terminatedAt` | `timestamptz` | an observation, so an instant |
| `termStart`, `termEnd` | `date` | agreed business dates, so calendar dates |
| `Money` × 5 | `numeric(19,4)` + one `varchar(3)` currency column | one currency per configuration; I-10 requires agreement |
| `LeasingFactor` | `numeric(9,6)` | six places — `numeric(5,2)` would round `1.9000%` and change every derived rate |

**The absent foreign key on `mlc_id` is a deliberate trade**, and it is the one a
DBA will object to. A real FK would give referential integrity and would also mean
the two contexts' tables cannot be separated without a migration, that a
`masterleasing` delete could be blocked by `individualleasing` rows, and that the
database enforces a relationship the architecture says is a published-API call. The
integrity gap is accepted: nothing deletes contracts (§ 6 of the master port), and
the lease's own lifecycle does not depend on the row still being there.


## 5. Constraints

- MUST NOT expose JPA entities, Spring Data types, or `Optional`.
- MUST NOT import `masterleasing` types of any kind, including its identity type.
- MUST NOT load partial aggregates.
- MUST NOT contain business logic.
- MUST NOT publish domain events or open transactions.
- MUST return `null` / an empty list rather than throwing for absence.


## 6. Known Gaps

- **Duplicate `elv_number` surfaces as `DuplicateKeyException`, not a domain
  exception.** Uniqueness is a genuine domain rule and it is enforced only by the
  database, so the failure reaches the resolver as an infrastructure exception and
  classifies as `INTERNAL_ERROR` rather than `CONFLICT`. Fixing it properly means an
  existence check before `save` (a race, therefore not a fix) or catching and
  translating the constraint violation in the adapter (viable, and the likely
  answer). Recorded rather than half-done.
- **No uniqueness on `bike_id` or `kau_number`.** One bike could be leased twice
  concurrently. Whether that is an error is a business question nobody has answered;
  it is not assumed here.
- **No optimistic locking**, same as the master-contract side.
- **No `delete`.** Leases are terminated, never removed.
- **No `findByJobCyclistId`.** No use case; when one appears it is a read-side list
  view, not an aggregate load.
