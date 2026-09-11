# Port Specification – CancelMasterLeasingContractUseCase (Inport)

## Purpose

The inbound application boundary for UC04 – CancelMasterLeasingContract: terminating
a **LRV** and every live lease beneath it.

**This is the only use case in the system that opens a transaction spanning two
bounded contexts.**

SDD: See `documentation/use-cases/uc04-cancel-master-leasing-contract.spec.md`


## 1. Interface

```
masterleasing.core.inport.usecase.CancelMasterLeasingContractUseCase
```

```kotlin
fun cancel(
    command: CancelMasterLeasingContractCommand,
): CancelMasterLeasingContractResult
```


## 2. Method Contract

### 2.1 Input: CancelMasterLeasingContractCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank, must parse as a UUID |
| `cancelledDate` | `LocalDate` | required; must not precede the contract's `activationDate` (I-08) |
| `reason` | `String` | **required**, non-blank, at most 400 characters (I-14) |

**`reason` is non-nullable here and nullable on UC07.** A framework agreement is
terminated by one commercial party against another on a stated ground, and the
ground has contractual consequences — notice period, early claim, return quota. One
employee ending one lease is routine and needs no justification recorded. The
asymmetry is deliberate and is one reason the two contexts declare separate
`CancellationReason` types (ADR 0003).

**`cancelledDate` is an input; the recording time is not.** A cancellation is dated
by the notice the parties exchanged, not by when somebody typed it in
(`architecture.definition.md` § 8.1). The command has no timestamp field.

### 2.2 Output: CancelMasterLeasingContractResult

| Field | Type | Description |
|---|---|---|
| `masterLeasingContractId` | `String` | |
| `status` | `String` | always `"CANCELLED"` on success |
| `cancelledDate` | `LocalDate` | echoed back |
| `terminatedLeaseCount` | `Int` | from the callee; how many leases fell with the contract |

`terminatedLeaseCount` is the blast radius. An operator cancelling a contract for a
400-employee company should see whether 3 or 300 leases just ended, and it is the
only observable evidence at the API surface that the fan-out happened at all.

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is not `ACTIVE` (I-07), including a second cancellation | `CONFLICT` |
| `InvalidMasterLeasingContractException` | `cancelledDate` before `activationDate` (I-08), or an invalid reason (I-14) | `BAD_REQUEST` |
| `IllegalArgumentException` | malformed id | `BAD_REQUEST` |
| *any exception from `individualleasing`* | a lease termination failed | propagated untouched; the shared transaction rolls back |

**The callee's exceptions are not caught or translated.** There is no partial
success to report, by construction: the fan-out shares this transaction.


## 3. Transaction Boundary

**Owned by the driver, and shared with `individualleasing`.**

The driver calls
`individualleasing.core.inport.usecase.TerminateContractsByMasterContractUseCase`,
which joins this transaction with `REQUIRED` propagation. All four conditions in
`architecture.definition.md` § 10 are met, and UC04 § 6 walks through them.

Two properties follow and both are part of this port's contract:

- **Atomicity across contexts.** Either the contract is cancelled and every live
  lease is terminated, or neither happened.
- **Lock duration scales with the lease count.** A 400-lease contract holds row
  locks on all of them for the duration. Accepted as the cost of atomicity; a batch
  limit would mean partial cancellation, which is the state this design exists to
  prevent.

**Ordering within the transaction is load-bearing**: the master contract's own guard
runs **before** the fan-out, so an invalid cancellation never terminates a single
lease. Reversing them would make the failure path depend on the rollback working
rather than on the order being right.


## 4. Implementation

`masterleasing.inbound.driver.CancelMasterLeasingContractDriver`.

It is the **only** class permitted to call the other context's inport —
`ContextRegistryTest.masterLeasingReachesIndividualLeasingInport_onlyFromADriver`
fails the build if a controller, listener or adapter does
(`architecture.definition.md` § 11 rule 3).


## 5. Constraints

- The interface MUST remain framework-free.
- `reason` MUST stay non-nullable (§ 2.1).
- The driver MUST NOT catch the callee's exceptions.
- The callee MUST join with `REQUIRED`; a `REQUIRES_NEW` callee would give the
  illusion of atomicity while committing independently
  (`terminate-contracts-by-master-contract.inport.spec.md` § 3).
- Moving this interaction to the domain-event model would be a cross-context
  interaction model change and an ADR (`sdd.playbook.md` § 6 item 10).


## 6. Known Gaps

- **Neither context can be deployed without the other** while this transaction is
  shared. Recorded in UC04 § 6 as an accepted cost.
- **No batch limit** on the fan-out, § 3.
- **The reason-length agreement between the two contexts is by convention**, and a
  divergence would reject a valid cancellation
  (`terminate-contracts-by-master-contract.inport.spec.md` § 2.3).
- **A second cancellation is a `CONFLICT`, not a no-op.** If a client legitimately
  needs retry-safety here, an idempotency key is the shape — and it is a contract
  change.
