# Port Specification – ActivateIndividualLeasingContractsUseCase (Inport)

## Purpose

The inbound application boundary for UC06 – ActivateIndividualLeasingContracts:
releasing every lease that was waiting for its master contract to become effective.

SDD: See `documentation/use-cases/uc06-activate-individual-leasing-contracts.spec.md`


## 1. Interface

```
individualleasing.core.inport.usecase.ActivateIndividualLeasingContractsUseCase
```

```kotlin
fun activate(
    command: ActivateIndividualLeasingContractsCommand,
): ActivateIndividualLeasingContractsResult
```

Framework-free, and **plural by design**: this is a fan-out over every pending lease
under one master contract, not an operation on one lease. A single-lease variant
would push the "which leases" decision into the listener, which is business logic in
an inbound adapter (`architecture.definition.md` § 4.8).


## 2. Method Contract

### 2.1 Input: ActivateIndividualLeasingContractsCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank; **opaque** (ADR 0005 category 2) |
| `activatedAt` | `Instant` | the moment `masterleasing` recorded the activation |

**`activatedAt` comes from the event, not from `ClockPort`.** Second row of
`architecture.definition.md` § 8.1's table: a fact already recorded in the
originating context, crossing a boundary. Re-dating it here would make a lease claim
it activated at a different moment than its contract did, and the two would drift
apart by the event's delivery latency — with neither looking wrong on its own.
`ClockPort` is the fallback only if the event carries no timestamp.

### 2.2 Output: ActivateIndividualLeasingContractsResult

| Field | Type | Description |
|---|---|---|
| `activatedCount` | `Int` | how many leases were activated |

There is no client to return it to; the listener logs it. It exists so a driver test
can assert the fan-out size, which is otherwise observable only through published
events.

### 2.3 Exceptions

None as part of the contract. The use case is tolerant by construction:

- a contract with no pending leases activates zero and succeeds — **not** a
  `NOT_FOUND`;
- a lease that is already `ACTIVE` is an idempotent no-op, because an `AFTER_COMMIT`
  listener can see the same event twice;
- terminal leases are excluded by the query rather than reaching the aggregate.

An unexpected exception rolls back **this** use case's own transaction and nothing
else. It does not reach `masterleasing`, which committed before the event was
published.


## 3. Transaction Boundary

**The implementation carries `@Transactional(propagation = REQUIRES_NEW)`, and that
is part of the contract.**

It runs `AFTER_COMMIT` of `masterleasing`'s transaction, so there is nothing to
join. Without `REQUIRES_NEW`, each repository call would run in auto-commit, one
statement at a time, and a fan-out that failed halfway would leave half the leases
activated with nothing to indicate it (ADR 0002).

**This is the exact opposite of `TerminateContractsByMasterContractUseCase`**, which
must use the default `REQUIRED` so it joins UC04's transaction. The two drivers sit
in the same package and differ by one annotation attribute; getting them the wrong
way round produces a system that passes every test that does not force a rollback.
`CancelMasterLeasingContractRollbackIT` is the only test that distinguishes them.

### All the leases share one transaction, deliberately

`architecture.definition.md` § 10's guideline prefers one aggregate per transaction,
and this use case does not follow it. The exemption is the one that section names:
several instances of the **same** aggregate type, with no invariant spanning them.
Each `activate` is independent; the driver is a fan-out, not a consistency boundary.

The alternative — one transaction per lease plus an outbox to make the fan-out
reliable — is a substantially larger design for no invariant gained.


## 4. Implementation

`individualleasing.inbound.driver.ActivateIndividualLeasingContractsDriver`,
triggered by `individualleasing.inbound.listener.MasterLeasingContractActivatedListener`.

Selection is via
`IndividualLeasingContractRepository.findPendingActivationByMasterContractId(...)`.
The status criterion lives in the query, not in the listener or the driver: a filter
in the listener would be business logic in an inbound adapter, and it is also
load-bearing, since `activate` throws for a `TERMINATED` lease and one ineligible
member would roll back the whole batch
(`individual-leasing-contract-repository.outport.spec.md` § 2.4).

**The listener holds no logic.** It builds a command and calls this port. It performs
no status check, no filtering and no persistence access
(`architecture.definition.md` § 4.8).


## 5. Constraints

- The interface MUST remain framework-free.
- The implementation MUST use `REQUIRES_NEW` (§ 3).
- `masterLeasingContractId` MUST stay a `String` and MUST NOT be parsed.
- MUST NOT be exposed over GraphQL. There is no operator-facing surface for this
  behaviour; it is a consequence of UC02.
- MUST remain idempotent under redelivery (§ 2.3).


## 6. Known Gaps

- **Delivery is not guaranteed** (ADR 0002). A JVM death between UC02's commit and
  this listener loses the event with no record, and leases stay
  `PENDING_ACTIVATION` under an `ACTIVE` contract with nothing to retry.

  Recovery today is a manual re-trigger, and there is **no operation that performs
  one** — no mutation, no admin endpoint. That is the sharpest form of this gap: the
  recovery path exists in principle and not in the system. An outbox is the fix, and
  a re-activation mutation is the cheaper interim one.
- **No batch limit.** A contract with 400 pending leases activates all of them in one
  transaction.
- **`activatedCount` is not persisted anywhere**, so there is no record after the
  fact of how many leases one activation released.
