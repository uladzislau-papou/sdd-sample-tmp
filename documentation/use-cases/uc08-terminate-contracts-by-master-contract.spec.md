# Use Case Specification – TerminateContractsByMasterContract

## Status
SPECIFIED

## Bounded Context
`individualleasing` — triggered by a **synchronous inport call** from
`masterleasing`'s UC04 driver. There is no GraphQL surface.

Cross-context. Owner: `individualleasing`. Caller: `masterleasing`.
Integration pattern: **shared transaction**, the callee joining with `REQUIRED`
(`architecture.definition.md` § 10, § 11 rule 3).

## Purpose

Terminate every live **ELV** issued under a **LRV** that is being cancelled, inside
the caller's transaction.


## 1. Intent

This is the other half of UC04. When a framework agreement is cancelled, the leases
beneath it end with it, attributed to `MASTER_CONTRACT` rather than to the lessee.

It exists as its own inport rather than as a repository call from `masterleasing`
because the decision of *which* leases are affected, and what terminating one means,
belongs to this context. If `masterleasing` selected them itself it would have to
know `IndividualLeasingContract`'s state model, and the boundary would be gone.


## 2. Input Contract

Not a client-facing operation. `TerminateContractsByMasterContractCommand` carries:

- `masterLeasingContractId` (String) — a plain `String`; owned by `masterleasing`
  and opaque here (ADR 0005 category 2). Used to select leases and as event payload;
  never parsed
- `terminatedAt` (Instant) — the moment the master contract recorded its cancellation
- `reason` (String, nullable) — the *Kündigungsgrund* text from the master contract

**`terminatedAt` comes from the caller, not from `ClockPort`.** Third row of
`architecture.definition.md` § 8.1's table: a fact already recorded in the
originating context, crossing a boundary. Re-dating it here would make a lease claim
it terminated at a different moment than its contract was cancelled, and the two
would drift apart with neither looking wrong alone. `ClockPort` is the fallback only
if the caller supplies nothing.

**The reason text crosses, but the type does not.** `masterleasing`'s
`CancellationReason` and this context's are different types for different facts
(ADR 0003), so the command carries a `String` and this context constructs its own
value object from it. That conversion can fail — see § 8.


## 3. Output Contract

Return type:
- `terminatedCount` (Int) — how many leases were terminated

UC04 returns this to its own caller as `terminatedLeaseCount`, so an operator sees
the blast radius of the cancellation they just performed.

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `InvalidIndividualLeasingContractException` | the reason text violates this context's `CancellationReason` invariant | Not applicable — surfaces through UC04 as `BAD_REQUEST` |
| `InvalidIndividualLeasingContractStateException` | a selected lease refuses termination | Not applicable — surfaces through UC04 |

**Nothing is caught or translated here.** An exception propagates to UC04 and rolls
back the shared transaction, which is the guarantee the arrangement exists to
provide. Swallowing a failure to "let the cancellation through" would produce
exactly the partial state § 1 forbids.


## 4. Preconditions

None. A master contract with no leases terminates zero and succeeds — that is a
legitimate outcome, not an error, and UC04's AC-02 depends on it.


## 5. Flow

1. Build this context's `CancellationReason` from the supplied text, when present
2. Load candidates via
   `IndividualLeasingContractRepository.findTerminableByMasterContractId(...)` —
   every **non-terminal** lease (`PENDING_ACTIVATION`, `ACTIVE`)
3. For each: `contract.terminate(terminatedAt, TerminatedBy.MASTER_CONTRACT, reason)`
4. Persist each via `update(...)`
5. Drain and publish `IndividualLeasingContractTerminatedByMasterContract` per lease
6. Return `terminatedCount`

**The driver carries `@Transactional` with default `REQUIRED` propagation**, so it
*joins* UC04's transaction rather than opening one. This is the opposite of UC06's
`REQUIRES_NEW` and the difference is the entire consistency guarantee: a
`REQUIRES_NEW` callee here would commit independently and give the illusion of
atomicity while the master contract's cancellation could still roll back
(`architecture.definition.md` § 10, condition 3).

**Step 2's selection criterion is the important one**, and it matters more here than
in any other query in the system. The criterion is `NOT IN (TERMINATED, EXPIRED)` —
stated as an exclusion rather than as `IN (PENDING_ACTIVATION, ACTIVE)` on purpose:
a new non-terminal status added to `IndividualLeasingContractStatus` is then
terminable by default, which is the safer direction to be wrong in. Listing the
terminable states would silently exclude it and leave those leases live after their
contract was cancelled — the exact failure UC04 exists to prevent.

That also makes the pinning integration test matter more than for other queries: a
mistake here **over-selects**, and the aggregate's guard would then throw on an
ineligible lease and roll back the whole cancellation.


## 6. Side Effects

- Persistence, **in the caller's transaction**: `status`, `terminated_at`,
  `terminated_by` and `cancellation_reason` updated on each affected lease
- Event publication: one `IndividualLeasingContractTerminatedByMasterContract` per
  lease, after the **shared** transaction commits — so a rollback in UC04 publishes
  nothing

### Idempotency is caller-dependent, and this is why

`IndividualLeasingContract.terminate` treats a second `MASTER_CONTRACT` termination
as a no-op while a second `LESSEE` termination throws (aggregate spec § 4). The
asymmetry exists for this use case specifically.

The fan-out runs over every lease under a contract inside one transaction. If a
lease somebody had already terminated caused a throw, the whole master-contract
cancellation would roll back and every other lease would stay live — one stale row
blocking a commercial decision. The no-op returns **before** mutating, so an
existing `LESSEE` attribution and its timestamp are never overwritten.

Step 2's query already excludes terminal leases, so the no-op should be unreachable
in practice. It is kept because "should be unreachable" and "is unreachable" differ
by one concurrent UC07, and the guard costs nothing.


## 7. Acceptance Criteria

**AC-01 – Fan-Out**
Given a master contract with two `ACTIVE` leases and one `PENDING_ACTIVATION`
When TerminateContractsByMasterContract is executed
Then all three become `TERMINATED` with `terminatedBy = MASTER_CONTRACT`
And `terminatedCount` is `3`

**AC-02 – No Leases**
Given a master contract with no leases
When TerminateContractsByMasterContract is executed
Then nothing is persisted, no event is published, and `terminatedCount` is `0`

**AC-03 – Terminal Leases Are Excluded by the Query**
Given a master contract with one `ACTIVE` lease, one `TERMINATED` and one `EXPIRED`
When TerminateContractsByMasterContract is executed
Then `terminatedCount` is `1`
And the already-terminated lease keeps its original `terminatedBy`, timestamp and reason

**AC-04 – Termination Time Comes From the Caller**
Given a command carrying `terminatedAt = 2026-06-30T12:00:00Z`
When TerminateContractsByMasterContract is executed
Then every terminated lease records that instant
And `ClockPort` is not consulted

**AC-05 – The Callee Joins the Caller's Transaction**
Given a master-contract cancellation that fails after the leases were terminated
When the transaction rolls back
Then no lease is `TERMINATED` and no termination event is published

**AC-06 – Reason Text Crosses, Type Does Not**
Given a master-contract cancellation with reason `"Employer insolvency"`
When TerminateContractsByMasterContract is executed
Then each lease stores that text as **this context's** `CancellationReason`
And the command's property is a `String`, not `masterleasing`'s value object

**AC-07 – A Lease Failure Aborts the Whole Cancellation**
Given one lease whose termination throws
When TerminateContractsByMasterContract is executed
Then the exception propagates to UC04
And neither the master contract nor any other lease is modified


## 8. Failure Scenarios

| Scenario | Exception | Effect |
|----------|-----------|--------|
| Reason text blank or over 400 characters | `InvalidIndividualLeasingContractException` | propagates; UC04 classifies `BAD_REQUEST` |
| A selected lease refuses termination | `InvalidIndividualLeasingContractStateException` | propagates; shared transaction rolls back |
| Repository failure | infrastructure exception | propagates; shared transaction rolls back |

**The reason-text row is a real cross-context hazard and is worth naming.**
`masterleasing`'s `CancellationReason` and this one both cap at 400 characters
today, so the conversion in step 1 cannot currently fail on length. Nothing enforces
that they stay in agreement. If `masterleasing` ever raised its ceiling, a
cancellation valid there would be rejected here and roll back a commercial
decision — a failure whose cause is two constants in two files that nobody thinks of
as related.

The alternatives are a shared type (rejected, ADR 0003) or the caller truncating
(rejected: silently altering a legal document's stated ground is worse than
failing). Left as a documented hazard, with a test on each side pinning the constant
so a change to either is visible in a diff.


## 9. GraphQL Contract

Not applicable — inport-triggered. There is no schema entry, no resolver, and no
`graphql/uc08-*.graphql` file.

The operator-facing surface for this behaviour is UC04's
`cancelMasterLeasingContract`, and `terminatedLeaseCount` in that payload is where
this use case becomes observable. `spec-documenter` must not create a request file
for this spec.


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `TerminateContractsByMasterContractDriverTest.terminate_terminatesEveryLiveLease`
- [ ] AC-02 covered by `TerminateContractsByMasterContractDriverTest.terminate_returnsZero_whenNoLeasesExist`
- [ ] AC-03 covered by `IndividualLeasingContractPersistenceAdapterIT.findTerminableByMasterContractId_returnsEveryNonTerminalLease`
      and `.findTerminableByMasterContractId_returnsEmpty_whenEveryLeaseIsTerminal`
      — the predicate is the guard and a stub cannot catch a wrong column or literal
- [ ] AC-04 covered by `TerminateContractsByMasterContractDriverTest.terminate_takesTheTimestampFromTheCommand_notTheClock`
- [ ] AC-05 covered by `CancelMasterLeasingContractRollbackIT` — the only test that
      can distinguish `REQUIRED` from `REQUIRES_NEW`
- [ ] AC-06 covered by `TerminateContractsByMasterContractDriverTest.terminate_buildsThisContextsCancellationReason`
- [ ] AC-07 covered by `CancelMasterLeasingContractRollbackIT.cancel_rollsBackTheContract_whenALeaseTerminationFails`
- [ ] Idempotency of a repeat `MASTER_CONTRACT` termination covered by
      `IndividualLeasingContractTest.terminate_byMasterContract_whenAlreadyTerminated_isIdempotentNoOp`
      and `.terminate_byMasterContract_whenAlreadyTerminated_doesNotOverwriteAttribution`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] The driver carries `@Transactional` with **default `REQUIRED`** propagation,
      and this is stated in `documentation/ports/terminate-contracts-by-master-contract.inport.spec.md`
- [ ] `findTerminableByMasterContractId` is specified in
      `documentation/ports/individual-leasing-contract-repository.outport.spec.md`,
      including why the criterion is written as an exclusion
- [ ] `IndividualLeasingContractTerminatedByMasterContract` exists in the aggregate's
      `event` package and carries `masterLeasingContractId` as a plain `String`
- [ ] The 400-character ceilings on both contexts' `CancellationReason` are each
      pinned by a test, per § 8
- [ ] **No `graphql/uc08-*.graphql` file exists**, per § 9

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
