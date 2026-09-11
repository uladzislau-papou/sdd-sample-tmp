# Port Specification – TerminateIndividualLeasingContractUseCase (Inport)

## Purpose

The inbound application boundary for UC07 – TerminateIndividualLeasingContract:
ending one **ELV** on the lessee's initiative.

SDD: See `documentation/use-cases/uc07-terminate-individual-leasing-contract.spec.md`


## 1. Interface

```
individualleasing.core.inport.usecase.TerminateIndividualLeasingContractUseCase
```

```kotlin
fun terminate(
    command: TerminateIndividualLeasingContractCommand,
): TerminateIndividualLeasingContractResult
```

**Singular**, against UC06's and UC08's plural fan-outs. One lessee ends one lease;
there is no "terminate all my leases" business case, and inventing one would make the
attribution ambiguous.


## 2. Method Contract

### 2.1 Input: TerminateIndividualLeasingContractCommand

| Field | Type | Constraint |
|---|---|---|
| `individualLeasingContractId` | `String` | non-blank, must parse as a UUID |
| `reason` | `String?` | optional; when present, non-blank and at most 400 characters (I-12) |

**`reason` is nullable here and non-nullable on UC04.** One employee ending one lease
is routine and needs no justification recorded; a lessor terminating a framework
agreement against an employer does, because the ground has contractual consequences.
The asymmetry is deliberate and is one reason the two contexts declare separate
`CancellationReason` types (ADR 0003).

An *absent* reason is `null`, not an empty string. Terminating without a reason is
permitted; terminating with a blank one is not, and the value object enforces that
rather than a schema constraint — so the rule holds for UC08 too, which reaches the
same aggregate function with no GraphQL input in the path.

**There is no `terminatedBy` field.** This port always attributes to
`TerminatedBy.LESSEE`, because it sits behind the lessee-facing mutation. A
master-contract termination arrives through UC08's separate inport, so this driver
never has to decide who is terminating — which means a caller cannot claim to be one.

**There is no timestamp field.** `terminatedAt` comes from `ClockPort`
(`architecture.definition.md` § 8.1): the time an action happened is the system's
observation, not the caller's claim.

### 2.2 Output: TerminateIndividualLeasingContractResult

| Field | Type | Description |
|---|---|---|
| `individualLeasingContractId` | `String` | |
| `status` | `String` | always `"TERMINATED"` on success |
| `terminatedBy` | `String` | always `"LESSEE"` on success |

`terminatedBy` is returned although it is constant for this port, so that a client
reading a termination response does not have to know which endpoint produced it.

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `IndividualLeasingContractNotFoundException` | no lease with the given id | `NOT_FOUND` |
| `InvalidIndividualLeasingContractStateException` | status is `TERMINATED` or `EXPIRED` (I-08) | `CONFLICT` |
| `InvalidIndividualLeasingContractException` | `reason` blank or over 400 characters (I-12) | `BAD_REQUEST` |
| `IllegalArgumentException` | malformed id | `BAD_REQUEST` |

**Both `CONFLICT` conditions are specific to the lessee path.** The aggregate's guard
is caller-dependent: a second `MASTER_CONTRACT` termination is an idempotent no-op
rather than an error (aggregate spec § 4). A lessee double-termination is a `CONFLICT`
because it is a deliberate act on an already-terminated lease and the client should
be told — but this table must not be read as the uniform contract of `terminate(...)`.

**A lease may be terminated from `PENDING_ACTIVATION`.** An application approved and
then withdrawn before the LRV took effect is a real case, and refusing it would leave
the lease stuck pending forever.


## 3. Transaction Boundary

Owned by the driver. One aggregate, one transaction. No cross-context call.


## 4. Implementation

`individualleasing.inbound.driver.TerminateIndividualLeasingContractDriver`.

It builds `CancellationReason` **before** loading the aggregate, so a malformed
reason is a `BAD_REQUEST` without a database round trip and never depends on whether
the lease happens to exist (UC07 § 5).


## 5. Constraints

- The interface MUST remain framework-free.
- The command MUST NOT gain a `terminatedBy` or a timestamp field (§ 2.1).
- The driver MUST always pass `TerminatedBy.LESSEE`.
- MUST NOT consult the master contract's return quota — see § 6.


## 6. Known Gaps

- **The return quota and early-claim fee are not enforced.**
  `MlcConfiguration.returnQuota`, `earlyClaimFeePercentage` and
  `earlyClaimWindowMonths` are inherited terms, and no use case counts terminations
  against them, so no fee is ever computed.

  Enforcing it means counting terminations per contract per year — a cross-aggregate
  question needing a read-side projection plus a policy passed in, the same shape as
  UC05's inherited-term checks. It is a use case of its own, not a line in this one.
  A quota check that is approximately right would be worse than none, because an
  early-claim fee is money charged to an employer.
- **No distinction between termination *reasons* that matter commercially.**
  Employee left, bike written off and quota claimed are all free text today. If the
  fee calculation above is ever built, at least one of those has to become a typed
  category, and that is a breaking change to this command.
- **No idempotency key.** A retried termination on an already-terminated lease is a
  `CONFLICT`, which is correct for a human client and awkward for an automated one.
