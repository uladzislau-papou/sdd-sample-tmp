# Use Case Specification – ActivateIndividualLeasingContracts

## Status
SPECIFIED

## Bounded Context
`individualleasing` — triggered by the `MasterLeasingContractActivated` domain
event, never by a GraphQL operation.

Cross-context. Owner: `individualleasing`. Publisher: `masterleasing` (UC02).
Integration pattern: **domain event**, `AFTER_COMMIT` + `REQUIRES_NEW`
(ADR 0002, `architecture.definition.md` § 10).

## Purpose

Release every lease that was issued while its **LRV** was still `DRAFT`, when that
contract becomes effective.


## 1. Intent

Employers collect applications before the framework agreement is countersigned, so
leases are routinely issued against a `DRAFT` contract and wait in
`PENDING_ACTIVATION` (UC05). When the contract is activated (UC02), they stop
waiting.

This is a **fan-out**: one event, N aggregates of the same type, one transaction.


## 2. Input Contract

Not a client-facing operation. The inbound adapter is
`MasterLeasingContractActivatedListener`, which receives:

- `masterLeasingContractId` (String) — a plain `String`; the identity is owned by
  `masterleasing` and is opaque here (ADR 0005 category 2)
- `activationDate` (LocalDate) — carried but not used by this use case
- `activatedAt` (Instant) — the moment `masterleasing` recorded the activation

The command handed to the driver carries `masterLeasingContractId` and
`activatedAt`.

**`activatedAt` comes from the event, not from `ClockPort`.** This is the second row
of `architecture.definition.md` § 8.1's table: a fact already recorded in the
originating context, crossing a boundary. Re-dating it with this context's clock
would make a lease claim it activated at a different moment than its contract did,
and the two would drift apart by the event's delivery latency — with neither looking
wrong on its own. `ClockPort` is the fallback only if the event carries no timestamp.


## 3. Output Contract

Return type:
- `activatedCount` (Int) — how many leases were activated

There is no client to return it to; the listener logs it. It exists so the driver
test can assert the fan-out size, which is otherwise only observable through
published events.

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| — | — | Not applicable — no GraphQL surface |

An exception here rolls back this use case's own `REQUIRES_NEW` transaction. It does
**not** roll back UC02, which committed before the event was published. That is the
consequence ADR 0002 accepts and § 6 discusses.


## 4. Preconditions

None on the caller's side. The use case is tolerant by construction:

- A contract with no pending leases activates zero and succeeds.
- A lease that is already `ACTIVE` is an idempotent no-op, because an
  `AFTER_COMMIT` listener can see the same event twice.


## 5. Flow

1. `MasterLeasingContractActivatedListener` receives the event `AFTER_COMMIT`
2. It builds `ActivateIndividualLeasingContractsCommand` and calls the inport —
   **it contains no business logic and makes no decision**
   (`architecture.definition.md` § 4.8)
3. The driver, in a `REQUIRES_NEW` transaction, loads candidates via
   `IndividualLeasingContractRepository.findPendingActivationByMasterContractId(...)`
4. For each: `contract.activate(activatedAt)`, then `update(...)`
5. Drain and publish `IndividualLeasingContractActivated` per lease
6. Return `activatedCount`

**Step 3's query carries the status criterion, and that is deliberate.** A
`status == PENDING_ACTIVATION` filter in the listener would be business logic in an
inbound adapter (§ 4.8). It is also load-bearing: the whole fan-out runs in one
transaction, and `activate` throws for a `TERMINATED` lease — so relying on the
aggregate guard alone would let one ineligible lease roll back the entire batch.
The aggregate's guard still holds independently; the query only selects candidates
(`architecture.definition.md` § 4.6).


## 6. Side Effects

- Persistence: `status` updated on each activated lease, in one `REQUIRES_NEW`
  transaction
- Event publication: one `IndividualLeasingContractActivated` per lease, after this
  transaction commits

### Multiple aggregates in one transaction, deliberately

`architecture.definition.md` § 10's guideline prefers one aggregate per transaction.
This use case does not follow it, and the exemption is the one that section names:
several instances of the **same** aggregate type, with no invariant spanning them.
Each `activate` is independent; the driver is a fan-out, not a consistency boundary.

The alternative — one transaction per lease plus an outbox to make the fan-out
reliable — is a substantially larger design for no invariant gained.

### What happens if this never runs

ADR 0002's known limitation: a JVM death between UC02's commit and this listener
loses the event with no record. Leases stay `PENDING_ACTIVATION` under an `ACTIVE`
contract, and nothing retries.

This is the recoverable half of the contrast in `architecture.definition.md` § 10 —
nobody was told anything untrue, and a re-trigger fixes it. It is why activation was
put on the event path and cancellation (UC04) was not.


## 7. Acceptance Criteria

**AC-01 – Fan-Out**
Given an activated LRV with three leases in `PENDING_ACTIVATION`
When ActivateIndividualLeasingContracts runs
Then all three become `ACTIVE`
And three `IndividualLeasingContractActivated` events are published
And `activatedCount` is `3`

**AC-02 – No Candidates**
Given an activated LRV with no pending leases
When ActivateIndividualLeasingContracts runs
Then nothing is persisted, no event is published, and `activatedCount` is `0`

**AC-03 – Already Active Leases Are Skipped**
Given an activated LRV with one `PENDING_ACTIVATION` lease and one already `ACTIVE`
When ActivateIndividualLeasingContracts runs
Then `activatedCount` is `1`
And the already-active lease emits no event

**AC-04 – Terminal Leases Do Not Break the Batch**
Given an activated LRV with two `PENDING_ACTIVATION` leases and one `TERMINATED`
When ActivateIndividualLeasingContracts runs
Then both pending leases become `ACTIVE`
And the terminated lease is untouched and the transaction does not roll back

**AC-05 – Redelivery Is Idempotent**
Given the event is delivered twice for the same contract
When ActivateIndividualLeasingContracts runs the second time
Then no lease changes state and no event is published

**AC-06 – Activation Time Comes From the Event**
Given an event carrying `activatedAt = 2026-04-01T09:00:00Z`
When ActivateIndividualLeasingContracts runs
Then the emitted `IndividualLeasingContractActivated` carries that instant
And `ClockPort` is not consulted

**AC-07 – The Listener Holds No Logic**
Given any event
When the listener receives it
Then it constructs a command and calls the inport, and performs no status check,
  no filtering and no persistence access


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| A lease's `activate` throws | `InvalidIndividualLeasingContractStateException` | Not applicable — rolls back this transaction only |
| Repository failure | infrastructure exception | Not applicable |

There is no `NOT_FOUND`: a contract with no leases is a legitimate zero-result
fan-out, not an error.

**A failure here does not roll back UC02**, which committed before the event was
published. The master contract stays `ACTIVE` and its leases stay pending. That is
ADR 0002's accepted limitation, not a defect in this use case.


## 9. GraphQL Contract

Not applicable — event-driven. There is no schema entry, no resolver, and no
`graphql/uc06-*.graphql` file.

This is the one use case with no request file, and the absence is deliberate rather
than an oversight: `CLAUDE.md`'s GraphQL Operation Documentation rule applies to
operations, and this use case exposes none. `spec-documenter` must not create one.


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `ActivateIndividualLeasingContractsDriverTest.activate_activatesEveryPendingLease`
- [ ] AC-02 covered by `ActivateIndividualLeasingContractsDriverTest.activate_returnsZero_whenNoCandidates`
- [ ] AC-03 covered by `IndividualLeasingContractTest.activate_whenAlreadyActive_isIdempotentNoOp`
      and `ActivateIndividualLeasingContractsDriverTest.activate_publishesNoEvent_forAnAlreadyActiveLease`
- [ ] AC-04 covered by `IndividualLeasingContractPersistenceAdapterIT.findPendingActivationByMasterContractId_returnsOnlyPendingLeases`
      — the query is the guard, and a stub cannot catch a wrong predicate
- [ ] AC-05 covered by `ActivateIndividualLeasingContractsDriverTest.activate_isIdempotent_onRedelivery`
- [ ] AC-06 covered by `ActivateIndividualLeasingContractsDriverTest.activate_takesTheTimestampFromTheEvent_notTheClock`
- [ ] AC-07 covered by `MasterLeasingContractActivatedListenerTest.listener_delegatesToTheInport_withoutFiltering`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `MasterLeasingContractActivatedListener` lives in `individualleasing.inbound.listener`
      and carries `@TransactionalEventListener(phase = AFTER_COMMIT)`
- [ ] The driver carries `@Transactional(propagation = REQUIRES_NEW)` — required
      because there is no transaction to join after commit (ADR 0002)
- [ ] `findPendingActivationByMasterContractId` is specified in
      `documentation/ports/individual-leasing-contract-repository.outport.spec.md`,
      including why the status criterion lives in the query
- [ ] `documentation/ports/activate-individual-leasing-contracts.inport.spec.md` reflects the inport
- [ ] **No `graphql/uc06-*.graphql` file exists**, per § 9

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
