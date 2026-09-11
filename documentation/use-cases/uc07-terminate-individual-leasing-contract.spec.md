# Use Case Specification – TerminateIndividualLeasingContract

## Status
SPECIFIED

## Bounded Context
`individualleasing` — triggered via GraphQL mutation by an external client, on the
lessee's initiative.

## Purpose

End one **ELV** early, on the employee's side, recording when and optionally why.


## 1. Intent

A lease ends before its term for ordinary reasons: the employee leaves the company,
the bike is written off, the employer claims part of its return quota
(*Rückgabekontingent*).

This use case records that, attributed to the **lessee**, so it is distinguishable
downstream from a lease that fell because its framework agreement was cancelled
(UC08). The attribution is the point: the two have different commercial
consequences — an early claim against the quota may carry a fee, a
master-contract cancellation does not.


## 2. Input Contract

Fields:
- `individualLeasingContractId` (ID) — required
- `reason` (String) — **optional**; free text, non-blank and at most 400 characters
  when supplied

Validation rules:
- `individualLeasingContractId` must be a well-formed UUID
- `reason`, when present, is validated by this context's `CancellationReason` (I-12).
  An *absent* reason is the absence of the object, not an empty one

**The reason is optional here and mandatory on UC04.** One employee ending one lease
is routine and needs no justification recorded; a lessor terminating a framework
agreement against an employer does. The asymmetry is deliberate and is one of the
reasons the two contexts declare separate `CancellationReason` types (ADR 0003).

**No timestamp input.** `terminatedAt` comes from `ClockPort`
(`architecture.definition.md` § 8.1) — the time the termination was recorded is the
system's observation, not the caller's claim. The input type has no such field.

The 400-character ceiling is enforced by the value object rather than by a schema
constraint, so that it holds for every caller: UC08 reaches the same aggregate
function from the `masterleasing` side with no GraphQL input in the path.


## 3. Output Contract

Return type:
- `individualLeasingContractId` (ID)
- `status` (String) — always `"TERMINATED"` on success
- `terminatedBy` (String) — always `"LESSEE"` on success

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `IndividualLeasingContractNotFoundException` | no lease with the given id | `NOT_FOUND` |
| `InvalidIndividualLeasingContractStateException` | status is `TERMINATED` or `EXPIRED` (I-08) | `CONFLICT` |
| `InvalidIndividualLeasingContractException` | `reason` blank or over 400 characters (I-12) | `BAD_REQUEST` |
| `IllegalArgumentException` | malformed id | `BAD_REQUEST` |


## 4. Preconditions

- The lease exists
- Its status is `PENDING_ACTIVATION` or `ACTIVE`

**A lease may be terminated before it was ever activated.** An application approved
and then withdrawn before the LRV took effect is a real case, and refusing it would
leave the lease stuck pending forever.

**A second lessee termination is a conflict, not a no-op** — unlike the
`MASTER_CONTRACT` party, for which it is idempotent (aggregate spec § 4). This is a
deliberate act on an already-terminated lease and the client should be told.


## 5. Flow

1. Parse `IndividualLeasingContractId`
2. Build `CancellationReason` when a reason was supplied → `BAD_REQUEST` if blank or
   over-long. An absent reason stays absent; the value object is simply not
   constructed
3. Read `terminatedAt` from `ClockPort.now()` — never from the input
4. Load via `IndividualLeasingContractRepository.findById(...)` → throw
   `IndividualLeasingContractNotFoundException` if absent
5. `contract.terminate(terminatedAt, TerminatedBy.LESSEE, reason)` → throws
   `InvalidIndividualLeasingContractStateException` if the state does not permit
   this party to terminate
6. Persist via `IndividualLeasingContractRepository.update(...)`
7. Publish `IndividualLeasingContractTerminatedByLessee` via `DomainEventPublisher`
8. Return `{ individualLeasingContractId, status: "TERMINATED", terminatedBy: "LESSEE" }`

Ordering note: **step 2 precedes step 4 deliberately.** A malformed reason is
rejected without a database round trip, and a `BAD_REQUEST` never depends on whether
the lease happens to exist.


## 6. Side Effects

- Persistence: `status`, `terminated_at`, `terminated_by` and `cancellation_reason`
  updated on `individual_leasing_contract`. All four columns are nullable, added by
  `V2__DDL_create_individual_leasing_contract.sql`, since a live lease has no values
  for them
- Event publication: `IndividualLeasingContractTerminatedByLessee`, after commit
  (ADR 0002), context-local

### Why this stores its timestamp when UC06 does not

`activate` deliberately discards `activatedAt` (UC06 § 2), so storing `terminatedAt`
here needs a reason rather than a precedent.

They are different kinds of fact. Activation is the *master contract's* fact
arriving from another context, carried on an event for whoever cares, and no lease
invariant needs it. Termination is this aggregate's own act — who terminated and why
is attribution the lease owns, it is the entire point of distinguishing UC07 from
UC08, and AC-05 depends on being able to observe that an existing attribution was
not overwritten. State the aggregate must be able to answer questions about belongs
in the aggregate (`modelling.definition.md`, What an aggregate stores).

**The return quota is not consulted and no early-claim fee is computed.** Both are
inherited terms on the master contract, and enforcing them means counting
terminations per contract per year — a cross-aggregate question. Recorded as an open
item in the aggregate spec § 7.


## 7. Acceptance Criteria

**AC-01 – Happy Path from ACTIVE**
Given a lease in `ACTIVE`
When TerminateIndividualLeasingContract is executed
Then the status becomes `TERMINATED` with `terminatedBy = LESSEE`
And `IndividualLeasingContractTerminatedByLessee` is published after commit

**AC-02 – Happy Path from PENDING_ACTIVATION**
Given a lease in `PENDING_ACTIVATION`
When TerminateIndividualLeasingContract is executed
Then the status becomes `TERMINATED` with `terminatedBy = LESSEE`

**AC-03 – Reason Recorded**
Given a termination with a reason supplied
When TerminateIndividualLeasingContract is executed
Then the reason is persisted and carried on the emitted event

**AC-04 – Reason Omitted**
Given a termination with no reason
When TerminateIndividualLeasingContract is executed
Then the termination succeeds, the stored reason is absent, and the emitted event
  carries a null reason

**AC-05 – Already Terminated**
Given a lease already `TERMINATED` by the master contract
When TerminateIndividualLeasingContract is executed
Then `InvalidIndividualLeasingContractStateException` is raised with classification
  `CONFLICT`
And the existing `terminatedBy = MASTER_CONTRACT`, timestamp and reason are **not**
  overwritten

**AC-06 – Expired Lease**
Given a lease in `EXPIRED`
When TerminateIndividualLeasingContract is executed
Then classification `CONFLICT` is returned and the status is unchanged

**AC-07 – Termination Time Comes From the Clock**
Given a lease in a terminable state
When TerminateIndividualLeasingContract is executed
Then `ClockPort` supplies `terminatedAt`
And the GraphQL input type has no such field

**AC-08 – Invalid Reason**
Given a termination whose reason is blank, and separately one of 401 characters
When TerminateIndividualLeasingContract is executed
Then classification `BAD_REQUEST` is returned and nothing is persisted
And a reason of exactly 400 characters is accepted

**AC-09 – Lease Not Found**
Given no lease exists for the given id
When TerminateIndividualLeasingContract is executed
Then classification `NOT_FOUND` is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Lease does not exist | `IndividualLeasingContractNotFoundException` | `NOT_FOUND` |
| Lease already `TERMINATED` | `InvalidIndividualLeasingContractStateException` | `CONFLICT` |
| Lease `EXPIRED` | `InvalidIndividualLeasingContractStateException` | `CONFLICT` |
| Blank or over-long reason | `InvalidIndividualLeasingContractException` | `BAD_REQUEST` |
| Malformed id | `IllegalArgumentException` | `BAD_REQUEST` |

Both `CONFLICT` rows are **specific to the lessee path**. The aggregate's guard is
caller-dependent: a second `MASTER_CONTRACT` termination is an idempotent no-op
rather than an error (aggregate spec § 4). Nothing here changes that — a lessee
double-termination is still a `CONFLICT` — but this table must not be read as the
uniform contract of `terminate(...)`.


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/individualleasing/individual-leasing-contract.graphqls`):

```graphql
input TerminateIndividualLeasingContractInput {
  individualLeasingContractId: ID!
  reason: String
}

type TerminateIndividualLeasingContractPayload {
  individualLeasingContractId: ID!
  status: String!
  terminatedBy: String!
}

extend type Mutation {
  terminateIndividualLeasingContract(
    input: TerminateIndividualLeasingContractInput!
  ): TerminateIndividualLeasingContractPayload!
}
```

`reason: String` — nullable, against UC04's `reason: String!`. The schema is where a
client first sees that difference, which is the strongest place for it to live.

Operation:
```graphql
mutation TerminateIndividualLeasingContract(
  $input: TerminateIndividualLeasingContractInput!
) {
  terminateIndividualLeasingContract(input: $input) {
    individualLeasingContractId
    status
    terminatedBy
  }
}
```

Response:
```json
{
  "data": {
    "terminateIndividualLeasingContract": {
      "individualLeasingContractId": "9ab2...",
      "status": "TERMINATED",
      "terminatedBy": "LESSEE"
    }
  }
}
```

Error classification mapping:
- success – lease terminated by the lessee
- `NOT_FOUND` – no lease with that id
- `CONFLICT` – status is `TERMINATED` or `EXPIRED`
- `BAD_REQUEST` – invalid reason, malformed id


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `IndividualLeasingContractTest.terminate_byLessee_fromActive_recordsLesseeAttribution`
      and `TerminateIndividualLeasingContractDriverTest.terminate_publishesTerminatedByLesseeEvent`
- [ ] AC-02 covered by `IndividualLeasingContractTest.terminate_byLessee_fromPendingActivation_transitionsToTerminated`
- [ ] AC-03 covered by `IndividualLeasingContractTest.terminate_byLessee_recordsReason`
      and `TerminateIndividualLeasingContractDriverTest.terminate_publishesEventCarryingReason`
- [ ] AC-04 covered by `IndividualLeasingContractTest.terminate_withoutReason_leavesReasonAbsent`
      and `TerminateIndividualLeasingContractDriverTest.terminate_withoutReason_publishesEventWithNullReason`
- [ ] AC-05 covered by `IndividualLeasingContractTest.terminate_byLessee_whenAlreadyTerminated_throwsAndDoesNotOverwriteAttribution`
      and `IndividualLeasingContractGraphQLControllerTest.terminate_returnsConflict_whenAlreadyTerminated`
- [ ] AC-06 covered by `IndividualLeasingContractTest.terminate_byLessee_fromExpired_throwsInvalidIndividualLeasingContractStateException`
- [ ] AC-07 covered by `TerminateIndividualLeasingContractDriverTest.terminate_alwaysTakesTheTerminationTimeFromTheClock`,
      which also asserts the command has no `terminatedAt` property
- [ ] AC-08 covered by `CancellationReasonTest` (blank, 400, 401, and length counted
      before trimming) and
      `IndividualLeasingContractGraphQLControllerTest.terminate_returnsBadRequest_whenReasonInvalid`
- [ ] AC-09 covered by `TerminateIndividualLeasingContractDriverTest.terminate_throwsNotFound_whenLeaseIsAbsent`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `src/main/resources/graphql/individualleasing/individual-leasing-contract.graphqls` declares `terminateIndividualLeasingContract`
- [ ] `graphql/uc07-terminate-individual-leasing-contract.graphql` covers success with
      and without a reason, plus `NOT_FOUND`, `CONFLICT` and `BAD_REQUEST`
- [ ] `TerminatedBy` enum (`LESSEE`, `MASTER_CONTRACT`) exists in
      `individualleasing.core.domain.individualleasingcontract`
- [ ] `IndividualLeasingContractTerminatedByLessee` exists in that aggregate's `event` package
- [ ] Termination attribution round-trips, including the no-reason case —
      `IndividualLeasingContractPersistenceAdapterIT.update_persistsTerminationAttribution`,
      `.update_persistsTermination_withoutReason`,
      `.findById_returnsAbsentTerminationFields_forALiveLease`
- [ ] `documentation/ports/terminate-individual-leasing-contract.inport.spec.md` reflects the inport

### Governance
- [ ] The unenforced return quota (§ 6) is recorded as an open item in
      `documentation/domain/aggregate-individual-leasing-contract.spec.md` § 7
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
