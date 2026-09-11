# Port Specification – TerminateContractsByMasterContractUseCase (Inport)

## Purpose

The inbound application boundary through which `masterleasing` terminates every live
lease under a contract it is cancelling.

**This is a published cross-context API and the only mutating one in the system.**
Everything about its contract is shaped by that: it runs inside the caller's
transaction, it translates nothing, and it swallows nothing.

SDD: See `documentation/use-cases/uc08-terminate-contracts-by-master-contract.spec.md`
and `documentation/use-cases/uc04-cancel-master-leasing-contract.spec.md`


## 1. Interface

```
individualleasing.core.inport.usecase.TerminateContractsByMasterContractUseCase
```

```kotlin
fun terminate(
    command: TerminateContractsByMasterContractCommand,
): TerminateContractsByMasterContractResult
```

Framework-free. Called by `masterleasing.inbound.driver.CancelMasterLeasingContractDriver`
and by nothing else — `ContextRegistryTest.masterLeasingReachesIndividualLeasingInport_onlyFromADriver`
enforces that only a driver may reach it.


## 2. Method Contract

### 2.1 Input: TerminateContractsByMasterContractCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank; selects the leases and is carried onto the event |
| `terminatedAt` | `Instant` | the moment the master contract recorded its cancellation |
| `reason` | `String?` | the *Kündigungsgrund* text; validated by **this** context's `CancellationReason` |

**`masterLeasingContractId` is a `String` and stays opaque.** ADR 0005 category 2:
the identity is owned by the caller's context. This context uses it as a selector
and as event payload; it never parses it or branches on its structure.

**`terminatedAt` comes from the caller, not from `ClockPort`.** Third row of
`architecture.definition.md` § 8.1's table: a fact already recorded in the
originating context, crossing a boundary. Re-dating it here would make a lease claim
it terminated at a different moment than its contract was cancelled, and the two
would drift apart by the call's duration with neither looking wrong alone.

**`reason` is a `String`, not a value object.** `masterleasing`'s
`CancellationReason` and this context's are different types for different business
facts (ADR 0003), so the **text** crosses and this context constructs its own value
object from it. That conversion is a real failure point — see § 2.3.

### 2.2 Output: TerminateContractsByMasterContractResult

| Field | Type | Description |
|---|---|---|
| `terminatedCount` | `Int` | how many leases were terminated |

UC04 returns this to its caller as `terminatedLeaseCount`, so an operator sees the
blast radius of the cancellation they just performed. It is also the only thing a
driver test can assert about the fan-out size without inspecting published events.

A count rather than a list of ids, deliberately: the caller has no use for the ids —
it cannot address a lease, it does not own the type, and returning them would invite
it to.

### 2.3 Exceptions

| Exception | Condition | Effect on the caller |
|---|---|---|
| `InvalidIndividualLeasingContractException` | the reason text violates this context's `CancellationReason` invariant | propagates; UC04 classifies `BAD_REQUEST` |
| `InvalidIndividualLeasingContractStateException` | a selected lease refuses termination | propagates; shared transaction rolls back |

**Nothing is caught, translated, or logged-and-continued here.** An exception
propagates to the caller and rolls back the shared transaction. Swallowing one to
"let the cancellation through" would produce exactly the partial state UC04 exists to
prevent: a cancelled master contract with a live lease under it.

The caller sees this context's exception types. That is correct and is the same
arrangement in the opposite direction as `ReadLeasingTermsUseCase` —
`core.domain.<aggregate>.exception` is reachable from an inport contract
(`architecture.definition.md` § 4.2).

#### The reason-length hazard

Both contexts cap `CancellationReason` at 400 characters today, so the conversion
cannot currently fail on length. **Nothing enforces that they stay in agreement.**

If `masterleasing` ever raised its ceiling, a cancellation valid there would be
rejected here and would roll back a commercial decision — a failure whose cause is
two constants in two files that nobody thinks of as related.

The alternatives were a shared type (rejected, ADR 0003 — the two reasons are
different business facts) and the caller truncating (rejected — silently altering a
legal document's stated ground is worse than failing). It is left as a documented
hazard with a test on each side pinning its own constant, so a change to either is
visible in a diff.


## 3. Transaction Boundary

**The implementation carries `@Transactional` with the default `REQUIRED`
propagation, and this is part of the contract rather than an implementation
detail.**

It **joins** the caller's transaction. A `REQUIRES_NEW` here would commit
independently and give the *illusion* of atomicity while the master contract's
cancellation could still roll back — worse than not sharing at all, because the
divergence would be silent (`architecture.definition.md` § 10, condition 3).

This is the exact opposite of `ActivateIndividualLeasingContractsUseCase` (UC06),
which must be `REQUIRES_NEW` because it runs `AFTER_COMMIT` with no transaction to
join. The two sit in the same package and differ by one annotation attribute, and
getting them the wrong way round produces a system that passes every test that does
not force a rollback.

`CancelMasterLeasingContractRollbackIT` is the only test that distinguishes them,
which is why the DoD of both UC04 and UC08 names it explicitly.


## 4. Implementation

`individualleasing.inbound.driver.TerminateContractsByMasterContractDriver`.

Selection is via
`IndividualLeasingContractRepository.findTerminableByMasterContractId(...)`, whose
criterion is written as an **exclusion** (`NOT IN (TERMINATED, EXPIRED)`) so that a
future non-terminal status is terminable by default — see that port spec § 2.5 for
why that is the safer direction to be wrong in.


## 5. Idempotency

**Per-lease, not per-call.** Calling this twice for the same master contract
terminates the live leases the first time and returns `0` the second, because the
query excludes terminal leases.

Beneath that, the aggregate's `terminate` treats a repeat `MASTER_CONTRACT`
termination as a no-op that returns **before** mutating, so an existing `LESSEE`
attribution and its timestamp are never overwritten. That guard should be
unreachable given the query, and it is kept because "should be unreachable" and "is
unreachable" differ by one concurrent UC07.


## 6. Constraints

- MUST remain framework-free at the interface.
- MUST NOT catch or translate exceptions from the aggregate.
- MUST NOT open its own transaction.
- MUST NOT import any `masterleasing` type, including its identity and its
  `CancellationReason`.
- MUST NOT be called from a controller, a listener or an adapter — only from a
  driver in the calling context (`architecture.definition.md` § 11 rule 3).
- MUST NOT be exposed over GraphQL. The operator-facing surface for this behaviour
  is UC04's `cancelMasterLeasingContract`.


## 7. Known Gaps

- **No batch size limit.** A contract with 400 leases holds row locks on all of them
  for the duration of the caller's transaction. Accepted as the cost of atomicity
  (UC04 § 6); a limit would mean partial cancellation, which is the state the design
  exists to prevent.
- **Reason-length agreement is by convention**, § 2.3.
- **Both contexts fail together**, and neither can be deployed separately without
  revisiting the interaction model — which would be an ADR
  (`sdd.playbook.md` § 6 item 10).
