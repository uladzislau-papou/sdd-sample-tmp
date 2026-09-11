# Port Specification – MasterLeasingContractRepository (Outport)

## Purpose

Defines the persistence boundary for the `MasterLeasingContract` aggregate on the
write side. This outport abstracts the storage mechanism from the domain and
application layer.

SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md`


## 1. Interface

```
masterleasing.core.outport.MasterLeasingContractRepository
```

Framework-free. Exposes only domain types, `shared.domain` types and Kotlin
built-ins.

**No `jakarta.persistence` type, no `org.springframework.data` type, and no
`java.util.Optional` appears in any signature** (`architecture.definition.md` § 6
rule 6, `coding-style.definition.md` § 1.4). Absence is `T?`.

This is the rule JPA erodes most easily. Spring Data's own
`MasterLeasingContractJpaRepository` is a **different** interface living in
`outbound.persistence.write`; making it the outport would put
`org.springframework.data.repository.Repository` in the core and would return
managed entities to it. `DependencyRulesTest` fails the build if either happens
(ADR 0007).


## 2. Method Contracts

### 2.1 save

```kotlin
fun save(contract: MasterLeasingContract)
```

**Responsibility:** Persist a new `MasterLeasingContract` aggregate atomically,
including its `MlcConfiguration`.

**Preconditions:**
- No row may already exist for `contract.contractId`.

**Postconditions:**
- All fields of the aggregate **and of its configuration** are durably persisted.
- The operation is atomic — either both rows are written or neither is.

**Exceptions:** a duplicate id surfaces as Spring's `DuplicateKeyException`,
translated from the Postgres constraint violation. It is a programming error, not a
use case outcome: `MasterLeasingContractId` is generated before `save` is called.

**Idempotency:** Not required.

Used by: UC01.

### 2.2 findById

```kotlin
fun findById(contractId: MasterLeasingContractId): MasterLeasingContract?
```

**Responsibility:** Load one aggregate by identity, fully reconstituted, with its
current configuration.

**Postconditions:**
- `null` when no row matches — **not** an exception. Translating absence into
  `MasterLeasingContractNotFoundException` is the driver's job, because "not found"
  is a use case outcome (a `NOT_FOUND` classification) rather than a persistence
  failure.
- When present, rebuilt via `MasterLeasingContract.reconstitute(...)`, which
  deliberately skips creation-time invariant checks
  (`modelling.definition.md` § Rehydration Rule).
- The returned aggregate has **no pending domain events**.
- **The returned aggregate is detached.** See § 3.

Used by: UC02, UC03, UC04, UC09.

### 2.3 update

```kotlin
fun update(contract: MasterLeasingContract)
```

**Responsibility:** Persist state changes to an existing aggregate.

**Preconditions:**
- A row must already exist for `contract.contractId`.
- Called inside the driver's transaction.

**Postconditions:**
- **Every mutable property is written**: `status`, `activation_date`,
  `cancelled_date`, `cancellation_reason`, and the configuration — which on an
  amendment is a *different version* and must replace the stored one, not merge into
  it.
- Immutable fields (`id`, `employer_id`, `lessor_id`, `partner_number`,
  `parent_mlc_id`) are set once by `save` and must not change.

> **This contract is load-bearing — a standing obligation, not a field list.**
> The aggregate has five mutable properties (aggregate spec § 4). **Any new mutable
> property must be added to the write path *and* to an
> `IT.update_persists*` assertion in the same increment**, or the change will not
> survive a write and nothing will fail.
>
> This obligation is heavier under JPA than it looks. A mapper that rebuilds the
> entity from the aggregate writes everything by construction; one that mutates a
> loaded entity field by field silently drops whatever it forgets. The round-trip
> tests in `test.definition.md` § 2.3 are what makes the difference observable.
>
> The same obligation binds the individual-leasing side
> (`individual-leasing-contract-repository.outport.spec.md`).

**Exceptions:** an update affecting no row fails loudly rather than silently
no-opping.

**Idempotency:** Idempotent for identical aggregate state. It is not a no-op guard:
the aggregate decides whether a transition should happen.

Used by: UC02, UC03, UC04.


## 3. Transaction Boundary and Detachment

The transaction is owned by the calling driver, never by the repository. The
implementation MUST NOT start its own transaction; it participates in the active one
(`architecture.definition.md` § 4.4, § 4.6).

Owners: `RegisterMasterLeasingContractDriver`, `ActivateMasterLeasingContractDriver`,
`AmendMlcConfigurationDriver`, `CancelMasterLeasingContractDriver`,
`ReadLeasingTermsDriver`.

`CancelMasterLeasingContractDriver` is a special case: its transaction is **shared
with `individualleasing`** (UC04), so writes through this repository and through the
other context's are one unit (`architecture.definition.md` § 10).

### Detachment is part of the contract

**An aggregate returned by `findById` MUST be detached.** Mutating it without
calling `update` must leave the database unchanged.

This is the JPA rule most easily broken by accident. If the mapper shares references
with a managed entity, Hibernate's dirty checking writes the caller's mutations at
flush time and the driver's explicit `update` becomes decorative — every transaction
boundary in the system would then be approximately right and occasionally wrong
(ADR 0009).

Mapping to a separate aggregate class makes this structurally true today. It is
stated here because nothing structurally stops a future implementation returning
something managed, and pinned by
`MasterLeasingContractPersistenceAdapterIT.findById_returnsADetachedAggregate`.

Event publication is deferred to after commit by the `DomainEventPublisher` adapter,
so a rollback cannot leak an event for a change that never landed.


## 4. Reference Implementation

`masterleasing.outbound.persistence.write.MasterLeasingContractPersistenceAdapter`,
delegating to `MasterLeasingContractJpaRepository` (Spring Data) with mapping in
`MasterLeasingContractMapper`.

Technology: Spring Data JPA over PostgreSQL 17. Schema managed by Flyway
(`V1__DDL_create_master_leasing_contract.sql`).

Mapping notes:

| Domain | Column | Note |
|--------|--------|------|
| `MasterLeasingContractId` | `id uuid` | |
| `EmployerId`, `LessorId` | `varchar(64)` | external references, stored as text |
| `PartnerNumber` | `varchar(64)` | |
| `parentMasterLeasingContractId` | `parent_mlc_id uuid` | nullable; **no FK constraint**, because a base contract may legitimately be registered after an affiliated one |
| `MasterLeasingContractStatus` | `varchar(16)` via `name()` | not `@Enumerated(ORDINAL)` — an ordinal silently re-maps when a value is inserted into the enum |
| `activationDate`, `cancelledDate` | `date` | business dates, not instants (`technical.spec.md`) |
| `CancellationReason` | `varchar(400)` | width matches `MAX_LENGTH` as a backstop; the value object is the enforcing side |
| `Money` (credit limit, band) | `numeric(19,4)` + a `varchar(3)` currency column | one currency column per configuration, since I-09 requires agreement |
| `Percentage` (return quota) | `numeric(9,6)` | |

`@Enumerated(EnumType.STRING)` is mandatory for both enums. Ordinal storage is the
JPA default that most often survives review and then breaks on the next enum
addition.


## 5. Constraints

- MUST NOT expose JPA entities, Spring Data types, or `Optional` through this interface.
- MUST NOT load partial aggregates — the configuration always loads with the contract.
- Parameters and return types MUST be domain or `shared.domain` types only.
- MUST NOT contain business logic — status transitions belong to the aggregate.
- MUST NOT publish domain events.
- MUST NOT open or commit transactions.
- MUST return `null` rather than throwing for absence.
- MAY depend on `core.domain` and `core.outport` only, never on `inbound.*`
  (`architecture.definition.md` § 6 rule 5).


## 6. Known Gaps

- **No optimistic locking.** Two concurrent updates to the same contract can both
  read the same state and both write; the second wins. This is what makes UC03's
  `expectedCurrentVersion` a partial mitigation rather than concurrency control
  (UC03 § 8). A `@Version` column is the fix and is a persistence-strategy change,
  hence an ADR (`sdd.playbook.md` § 6 item 4).
- **No configuration history.** `update` replaces the configuration row rather than
  appending a version, so superseded terms are unrecoverable. An audit view needs a
  history table and its own use case.
- **No `delete`.** Deliberate — contracts are cancelled, never removed.
- **No `findByEmployerId`.** There is no use case for it. When one appears it
  probably belongs on the read side (`outbound.persistence.read`), not here: it
  would be a list view, not an aggregate load.
