# Use Case Specification – CancelMasterLeasingContract

## Status
SPECIFIED

## Bounded Context
`masterleasing` — triggered via GraphQL mutation by an external client.

Cross-context. Owner: `masterleasing`. Callee: `individualleasing` (UC08).
Integration pattern: **synchronous inport call inside one shared transaction**
(`architecture.definition.md` § 10, § 11 rule 3).

## Purpose

Terminate a **LRV** on a stated ground, and terminate every live lease issued under
it in the same transaction.


## 1. Intent

When a framework agreement ends, every *Einzel-Leasingvertrag* beneath it ends with
it. The two facts must be true together or neither must be recorded, because the
failure mode of recording only the first is a lessor who has been told the agreement
is over while employees still hold live leases — and the lessor keeps invoicing for
bikes on a terminated agreement.

This is the use case whose consistency requirement drives the cross-context
transaction rule in `architecture.definition.md` § 10.


## 2. Input Contract

Fields:
- `masterLeasingContractId` (ID) — required
- `cancelledDate` (LocalDate) — required; the date the LRV is terminated
- `reason` (String) — **required**; the *Kündigungsgrund*, non-blank, at most 400 characters

Validation rules:
- `cancelledDate` must not precede the contract's stored `activationDate` (I-08)
- `reason` is validated by `CancellationReason` (I-14)

**The reason is mandatory here and optional on an individual lease** (UC07). A
framework agreement is terminated by one commercial party against another on a
stated ground, and the ground has contractual consequences — notice period, early
claim, the return quota. One employee ending one lease because they changed jobs is
routine and needs no justification recorded. The asymmetry is deliberate and is why
the two contexts declare separate `CancellationReason` types (ADR 0003).

**`cancelledDate` is an input; the recording time is not.** Same distinction as
UC02's `activationDate` (`architecture.definition.md` § 8.1). A cancellation is
dated by the notice the parties exchanged, not by when somebody typed it in.

The 400-character ceiling is a deliberate number, not a guess: the reason is free
text a human reads, it is bounded so the column can be `varchar(400)` rather than
unbounded `text`, and leaving it unstated would ship as no validation at all.


## 3. Output Contract

Return type:
- `masterLeasingContractId` (ID)
- `status` (String) — always `"CANCELLED"` on success
- `cancelledDate` (LocalDate)
- `terminatedLeaseCount` (Int) — how many individual leases were terminated

`terminatedLeaseCount` is returned because the caller needs to know the blast
radius. An operator cancelling a contract for a 400-employee company should see
whether 3 or 300 leases just ended, and it is the only observable evidence at the
API surface that the fan-out happened at all.

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is not `ACTIVE` (I-07), including a second cancellation | `CONFLICT` |
| `InvalidMasterLeasingContractException` | `cancelledDate` before `activationDate` (I-08), or an invalid reason (I-14) | `BAD_REQUEST` |
| `IllegalArgumentException` | malformed id | `BAD_REQUEST` |

**A failure inside `individualleasing` surfaces as that context's exception, and
rolls this transaction back.** It is not translated or swallowed. There is no
partial success to report.


## 4. Preconditions

- The contract exists
- Its status is `ACTIVE`
- `cancelledDate >= activationDate`

A second cancellation is a **conflict, not a no-op** — unlike the individual-lease
side, where a second `MASTER_CONTRACT` termination is idempotent. The discriminator
is again the trigger: this is an operator action through a mutation, the lease side
is a fan-out that must tolerate one already-terminated member without rolling back
the batch.


## 5. Flow

1. Parse `MasterLeasingContractId`
2. Build `CancellationReason` from the input → `BAD_REQUEST` if blank or over-long
3. Read `now` from `ClockPort`
4. Load via `MasterLeasingContractRepository.findById(...)` → throw
   `MasterLeasingContractNotFoundException` if absent
5. `contract.cancel(cancelledDate, reason, now)` → throws on I-07 or I-08
6. Persist via `MasterLeasingContractRepository.update(...)`
7. **Call `individualleasing`'s inport**:
   `TerminateContractsByMasterContractUseCase.terminate(command)` (UC08), carrying
   the contract id as a `String`, `terminatedAt` derived from `cancelledDate`, and
   the reason text. The callee joins **this** transaction (`REQUIRED`)
8. Publish `MasterLeasingContractCancelled` via `DomainEventPublisher`
9. Return the payload, including the count UC08 reported

**Step 5 precedes step 7 deliberately.** The master contract's own guard runs first,
so an invalid cancellation never terminates a single lease. Reversing them would
mean a `CONFLICT` on the master contract *after* N leases had been terminated in the
same transaction — recoverable by rollback, but it would make the failure path
depend on the rollback working rather than on the order being right.

Step 2 precedes step 4 for the reason UC03 § 5 gives: an invalid reason is a
`BAD_REQUEST` without a database round trip.


## 6. Side Effects

- Persistence, in one transaction:
  - `master_leasing_contract`: `status`, `cancelled_date`, `cancellation_reason`
  - `individual_leasing_contract`: `status`, `terminated_at`, `terminated_by`,
    `cancellation_reason` for every affected lease
- Event publication, after commit:
  - `MasterLeasingContractCancelled` — **context-local**
  - `IndividualLeasingContractTerminatedByMasterContract` — one per lease, from UC08

### Why a shared transaction rather than a domain event

This is the decision `architecture.definition.md` § 10 licenses, and all four of its
conditions are met:

1. **Driver-to-inport.** `CancelMasterLeasingContractDriver` calls
   `individualleasing.core.inport.usecase.TerminateContractsByMasterContractUseCase`.
   No other layer may open a cross-context transaction, and
   `ContextRegistryTest.masterLeasingReachesIndividualLeasingInport_onlyFromADriver`
   enforces it.
2. **The caller needs confirmation before committing its own decision.** Stated at
   § 1: a cancelled contract with live leases under it is the failure this use case
   exists to prevent, and it has a direct financial consequence.
3. **The callee joins with `REQUIRED`**, never `REQUIRES_NEW`. A `REQUIRES_NEW`
   callee would commit independently and give the *illusion* of atomicity — worse
   than not sharing at all, because the divergence would be silent.
4. **No invariant spans the two aggregates.** They are updated together for
   consistency of outcome, not because either enforces a rule about the other. The
   master contract does not know how many leases exist and holds no collection of
   them (aggregate spec § 1).

**Why not an event, given UC02 uses one.** The contrast is the whole argument. If
UC02's fan-out fails, some leases stay pending under an active contract: recoverable,
invisible, and nothing untrue was said. If this fan-out failed the same way, the
untruth has already been delivered. An outbox would make delivery reliable but not
atomic — the inconsistency window shrinks rather than closes — and a saga with a
compensating un-cancel is worse still, since reversing a cancellation people may
already have been notified about is a second wrong.

**The accepted costs**, recorded so they are not rediscovered as surprises:

- The transaction stays open for the duration of N cross-context calls, so lock
  duration scales with the number of leases. With no optimistic locking and no batch
  size limit, a 400-lease contract holds row locks for all of them.
- Both contexts fail together, and neither can be deployed separately without
  revisiting this.
- Switching this interaction to the event model later is a cross-context interaction
  model change and an ADR (`sdd.playbook.md` § 6 item 10).


## 7. Acceptance Criteria

**AC-01 – Happy Path with Live Leases**
Given an `ACTIVE` contract activated `2026-01-01` with three `ACTIVE` leases
When CancelMasterLeasingContract is executed with `cancelledDate = 2026-06-30`
Then the contract status becomes `CANCELLED` with that date and the stated reason
And all three leases become `TERMINATED` with `terminatedBy = MASTER_CONTRACT`
And `terminatedLeaseCount` is `3`

**AC-02 – Happy Path with No Leases**
Given an `ACTIVE` contract with no leases issued under it
When CancelMasterLeasingContract is executed
Then the contract is cancelled and `terminatedLeaseCount` is `0`

**AC-03 – Terminal Leases Are Skipped, Not Failed**
Given an `ACTIVE` contract with one `ACTIVE` lease, one already `TERMINATED` and
  one `EXPIRED`
When CancelMasterLeasingContract is executed
Then the contract is cancelled, `terminatedLeaseCount` is `1`
And the already-terminated lease keeps its original attribution and timestamp

**AC-04 – A Lease Failure Rolls Back the Cancellation**
Given an `ACTIVE` contract whose lease termination fails
When CancelMasterLeasingContract is executed
Then the whole transaction rolls back
And the contract is still `ACTIVE` with no `cancelledDate`
And no lease is terminated

**AC-05 – Cancellation Date Before Activation**
Given a contract activated `2026-04-01`
When CancelMasterLeasingContract is executed with `cancelledDate = 2026-03-01`
Then `InvalidMasterLeasingContractException` is raised with classification
  `BAD_REQUEST`, the contract stays `ACTIVE`, and no lease is touched

**AC-06 – Reason Is Mandatory**
Given a cancellation with a blank reason, or one of 401 characters
When CancelMasterLeasingContract is executed
Then classification `BAD_REQUEST` is returned and nothing is persisted

**AC-07 – Already Cancelled**
Given a contract already `CANCELLED`
When CancelMasterLeasingContract is executed
Then `InvalidMasterLeasingContractStateException` is raised with classification
  `CONFLICT` and the existing `cancelledDate` and reason are not overwritten

**AC-08 – Guard Precedes Fan-Out**
Given a contract in `DRAFT` with leases issued under it
When CancelMasterLeasingContract is executed
Then classification `CONFLICT` is returned and **no lease is terminated**

**AC-09 – Contract Not Found**
Given no contract exists for the given id
When CancelMasterLeasingContract is executed
Then `MasterLeasingContractNotFoundException` is raised with classification `NOT_FOUND`


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Contract does not exist | `MasterLeasingContractNotFoundException` | `NOT_FOUND` |
| Contract not `ACTIVE`, including a second cancellation | `InvalidMasterLeasingContractStateException` | `CONFLICT` |
| `cancelledDate` before `activationDate` | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |
| Blank or over-long reason | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |
| Malformed id | `IllegalArgumentException` | `BAD_REQUEST` |
| A lease termination fails | the callee's exception, propagated | per that exception |

**No partial success is possible**, by construction: the fan-out shares this
transaction, so any failure rolls the whole thing back. That is the property
AC-04 exists to pin, and it is the one a future refactor toward an outbox would
silently remove.


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls`):

```graphql
input CancelMasterLeasingContractInput {
  masterLeasingContractId: ID!
  cancelledDate: String!
  reason: String!
}

type CancelMasterLeasingContractPayload {
  masterLeasingContractId: ID!
  status: String!
  cancelledDate: String!
  terminatedLeaseCount: Int!
}

extend type Mutation {
  cancelMasterLeasingContract(
    input: CancelMasterLeasingContractInput!
  ): CancelMasterLeasingContractPayload!
}
```

`reason: String!` — non-nullable in the schema, mirroring § 2. The schema is the
first place a client learns the reason is mandatory here and optional on UC07.

Operation:
```graphql
mutation CancelMasterLeasingContract($input: CancelMasterLeasingContractInput!) {
  cancelMasterLeasingContract(input: $input) {
    masterLeasingContractId
    status
    cancelledDate
    terminatedLeaseCount
  }
}
```

Response:
```json
{
  "data": {
    "cancelMasterLeasingContract": {
      "masterLeasingContractId": "3f1c...",
      "status": "CANCELLED",
      "cancelledDate": "2026-06-30",
      "terminatedLeaseCount": 3
    }
  }
}
```

Error classification mapping:
- success – contract and all live leases terminated
- `NOT_FOUND` – no contract with that id
- `CONFLICT` – status is not `ACTIVE`
- `BAD_REQUEST` – invalid reason, `cancelledDate` before activation, malformed id


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `CancelMasterLeasingContractIT.cancel_terminatesEveryLiveLease`
- [ ] AC-02 covered by `CancelMasterLeasingContractDriverTest.cancel_succeeds_whenNoLeasesExist`
- [ ] AC-03 covered by `CancelMasterLeasingContractIT.cancel_skipsTerminalLeases_andStillCancelsTheContract`
      and `IndividualLeasingContractTest.terminate_byMasterContract_whenAlreadyTerminated_doesNotOverwriteAttribution`
- [ ] AC-04 covered by `CancelMasterLeasingContractRollbackIT.cancel_rollsBackTheContract_whenALeaseTerminationFails`
- [ ] AC-05 covered by `MasterLeasingContractTest.cancel_throwsInvalidMasterLeasingContractException_whenDateBeforeActivation`
- [ ] AC-06 covered by `CancellationReasonTest` and
      `MasterLeasingContractGraphQLControllerTest.cancel_returnsBadRequest_whenReasonInvalid`
- [ ] AC-07 covered by `MasterLeasingContractTest.cancel_throwsInvalidMasterLeasingContractStateException_whenAlreadyCancelled`
      and `.cancel_whenAlreadyCancelled_doesNotOverwriteAttribution`
- [ ] AC-08 covered by `CancelMasterLeasingContractDriverTest.cancel_doesNotCallTheLeaseInport_whenTheContractGuardFails`
- [ ] AC-09 covered by `CancelMasterLeasingContractDriverTest.cancel_throwsNotFound_whenContractIsAbsent`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `cancelMasterLeasingContract`
- [ ] `graphql/uc04-cancel-master-leasing-contract.graphql` covers success, `NOT_FOUND`, `CONFLICT` and `BAD_REQUEST`
- [ ] The callee joins the caller's transaction with `REQUIRED` — asserted by
      `CancelMasterLeasingContractRollbackIT` (AC-04), which is the only test that can
      distinguish `REQUIRED` from `REQUIRES_NEW`
- [ ] `documentation/ports/terminate-contracts-by-master-contract.inport.spec.md` records
      the propagation requirement on the callee side
- [ ] `documentation/ports/cancel-master-leasing-contract.inport.spec.md` reflects the inport
- [ ] Cancellation attribution round-trips — `MasterLeasingContractPersistenceAdapterIT.update_persistsCancellationAttribution`

### Governance
- [ ] **No ADR required**, recorded here so the absence is a decision rather than an
      omission. The shared cross-context transaction is permitted by
      `architecture.definition.md` § 10, whose four conditions § 6 walks through
      explicitly. Establishing it does not *change* the cross-context interaction
      model (`sdd.playbook.md` § 6 item 10) — it applies the model the architecture
      definition already sets out. Moving this interaction to an event later **would**
      be a trigger
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
