# Use Case Specification – ActivateMasterLeasingContract

## Status
SPECIFIED

## Bounded Context
`masterleasing` — triggered via GraphQL mutation by an external client.

Publishes `MasterLeasingContractActivated` into `shared.domain.event`, which
`individualleasing` consumes (UC06). Integration pattern: **domain event**.

## Purpose

Make a countersigned **LRV** effective from a stated activation date, and release
every lease already issued under it.


## 1. Intent

A framework agreement becomes effective on a date the parties agreed. From that
moment leases may be issued against it (UC05), and leases that were issued while it
was still `DRAFT` — employers collect applications before signature — stop waiting
and become active (UC06).

Activation is therefore two things at once: a state change here, and a release
signal for another context. That is why it is the one event in this aggregate that
lives in the shared kernel.


## 2. Input Contract

Fields:
- `masterLeasingContractId` (ID) — required
- `activationDate` (LocalDate) — required; the date the LRV becomes effective

Validation rules:
- `masterLeasingContractId` must be a well-formed UUID
- `activationDate` is required and is **not** validated against the clock

**`activationDate` is an input, and `activatedAt` is not.** The distinction is
`architecture.definition.md` § 8.1's: `activationDate` is a commercial term the
parties agreed — routinely backdated to the start of a month, occasionally
forward-dated to a known start — so it is a business value the caller supplies.
`activatedAt`, the moment the system recorded the activation, is an observation and
comes from `ClockPort`. The input type has no field for it.

Deliberately **not** validated: that `activationDate` is not in the future, or not
far in the past. Both are legitimate. A rule bounding it would need a business
justification nobody has given, and an arbitrary window is worse than none because
it fails for the first real case that exceeds it.


## 3. Output Contract

Return type:
- `masterLeasingContractId` (ID)
- `status` (String) — always `"ACTIVE"` on success
- `activationDate` (LocalDate)

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `InvalidMasterLeasingContractStateException` | status is not `DRAFT` (I-04) | `CONFLICT` |
| `IllegalArgumentException` | `masterLeasingContractId` is not a well-formed UUID | `BAD_REQUEST` |


## 4. Preconditions

- The contract exists
- Its status is `DRAFT`

A second activation of an already-`ACTIVE` contract is a **conflict, not a no-op**.
This is the opposite of `IndividualLeasingContract.activate`, and the discriminator
is the trigger: this is an operator pressing a button, so a repeat is a mistake the
client should hear about. The individual-contract side is driven by a re-deliverable
event and must be idempotent (that aggregate's spec § 4).


## 5. Flow

1. Parse `MasterLeasingContractId` from the argument
2. Read `now` from `ClockPort`
3. Load via `MasterLeasingContractRepository.findById(...)` → throw
   `MasterLeasingContractNotFoundException` if absent
4. `contract.activate(activationDate, now)` → throws
   `InvalidMasterLeasingContractStateException` if not `DRAFT`
5. Persist via `MasterLeasingContractRepository.update(...)`
6. Publish `MasterLeasingContractActivated` via `DomainEventPublisher`
7. Return `{ masterLeasingContractId, status: "ACTIVE", activationDate }`

**Step 6 is where the fan-out begins and it is asynchronous by design.** The event
is published after this transaction commits; `individualleasing` activates its
pending leases in a separate `REQUIRES_NEW` transaction (UC06). This use case does
not know whether that succeeded and does not wait for it — see § 6.


## 6. Side Effects

- Persistence: `status`, `activation_date` updated on `master_leasing_contract`
- Event publication: `MasterLeasingContractActivated`, after commit (ADR 0002),
  **in `shared.domain.event`** because another context consumes it
  (`modelling.definition.md`, Domain Event)

### Why activation fans out by event while cancellation shares a transaction

This is the contrast `architecture.definition.md` § 10 draws, and UC02 is the
cheap half of it.

If the fan-out fails here, some leases stay `PENDING_ACTIVATION` under an `ACTIVE`
master contract. That is recoverable, invisible to the employer, and nobody was told
anything untrue — the contract genuinely is active. Re-triggering activates the
stragglers.

If UC04's fan-out failed the same way, the lessor would have been told the contract
was cancelled while employees still held live leases and the lessor kept invoicing.
That is a lie already delivered, so UC04 uses a shared transaction instead.

The accepted cost here is ADR 0002's known limitation: a JVM death between commit
and delivery loses the event with no record, and nothing retries. One lease stuck
pending is the worst case, and it is why this fan-out was the one put on the event
path.


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given a contract in `DRAFT`
When ActivateMasterLeasingContract is executed with `activationDate = 2026-04-01`
Then the status becomes `ACTIVE` and `activationDate` is stored as `2026-04-01`
And `MasterLeasingContractActivated` is published after commit carrying that date

**AC-02 – Activation Date Comes From the Caller, Recording Time From the Clock**
Given a contract in `DRAFT`
When ActivateMasterLeasingContract is executed
Then `activationDate` is the value supplied
And `activatedAt` on the emitted event is `ClockPort.now()`
And the GraphQL input type has no `activatedAt` field

**AC-03 – Backdated Activation Is Accepted**
Given a contract in `DRAFT` and an `activationDate` three months in the past
When ActivateMasterLeasingContract is executed
Then the activation succeeds

**AC-04 – Already Active**
Given a contract already in `ACTIVE`
When ActivateMasterLeasingContract is executed
Then `InvalidMasterLeasingContractStateException` is raised with classification
  `CONFLICT`, and neither the status nor the stored `activationDate` changes

**AC-05 – Cancelled Contract**
Given a contract in `CANCELLED`
When ActivateMasterLeasingContract is executed
Then `InvalidMasterLeasingContractStateException` is raised with classification `CONFLICT`

**AC-06 – Contract Not Found**
Given no contract exists for the given id
When ActivateMasterLeasingContract is executed
Then `MasterLeasingContractNotFoundException` is raised with classification
  `NOT_FOUND` and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Contract does not exist | `MasterLeasingContractNotFoundException` | `NOT_FOUND` |
| Contract already `ACTIVE` | `InvalidMasterLeasingContractStateException` | `CONFLICT` |
| Contract `CANCELLED` or `ENDED` | `InvalidMasterLeasingContractStateException` | `CONFLICT` |
| Malformed id | `IllegalArgumentException` | `BAD_REQUEST` |


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls`):

```graphql
input ActivateMasterLeasingContractInput {
  masterLeasingContractId: ID!
  activationDate: String!
}

type ActivateMasterLeasingContractPayload {
  masterLeasingContractId: ID!
  status: String!
  activationDate: String!
}

extend type Mutation {
  activateMasterLeasingContract(
    input: ActivateMasterLeasingContractInput!
  ): ActivateMasterLeasingContractPayload!
}
```

Dates are ISO-8601 `String!` for the same reason amounts are (UC01 § 9): no built-in
scalar carries them losslessly, and a custom `Date` scalar is a deferred schema
addition rather than a behaviour change.

Operation:
```graphql
mutation ActivateMasterLeasingContract($input: ActivateMasterLeasingContractInput!) {
  activateMasterLeasingContract(input: $input) {
    masterLeasingContractId
    status
    activationDate
  }
}
```

Response:
```json
{
  "data": {
    "activateMasterLeasingContract": {
      "masterLeasingContractId": "3f1c...",
      "status": "ACTIVE",
      "activationDate": "2026-04-01"
    }
  }
}
```

Error classification mapping:
- success – contract activated
- `NOT_FOUND` – no contract with that id
- `CONFLICT` – status is not `DRAFT`
- `BAD_REQUEST` – malformed id


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `MasterLeasingContractTest.activate_transitionsDraftToActive`
      and `ActivateMasterLeasingContractDriverTest.activate_publishesActivatedEvent`
- [ ] AC-02 covered by `ActivateMasterLeasingContractDriverTest.activate_takesRecordingTimeFromTheClock`,
      which also asserts the command has no `activatedAt` property
- [ ] AC-03 covered by `MasterLeasingContractTest.activate_acceptsABackdatedActivationDate`
- [ ] AC-04 covered by `MasterLeasingContractTest.activate_throwsInvalidMasterLeasingContractStateException_whenAlreadyActive`
      and `MasterLeasingContractGraphQLControllerTest.activate_returnsConflict_whenAlreadyActive`
- [ ] AC-05 covered by `MasterLeasingContractTest.activate_throwsInvalidMasterLeasingContractStateException_whenCancelled`
- [ ] AC-06 covered by `ActivateMasterLeasingContractDriverTest.activate_throwsNotFound_whenContractIsAbsent`
      and `MasterLeasingContractGraphQLControllerTest.activate_returnsNotFound_whenAbsent`
- [ ] Driver orchestration and event emission covered by `ActivateMasterLeasingContractDriverTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `MasterLeasingContractActivated` exists in **`shared.domain.event`** and carries
      the contract id as a plain `String` (ADR 0005 category 2)
- [ ] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `activateMasterLeasingContract`
- [ ] `graphql/uc02-activate-master-leasing-contract.graphql` covers success, `NOT_FOUND`, `CONFLICT` and `BAD_REQUEST`
- [ ] `activation_date` round-trips as a `date` column — covered by
      `MasterLeasingContractPersistenceAdapterIT.update_persistsActivationDate`
- [ ] `documentation/ports/activate-master-leasing-contract.inport.spec.md` reflects the inport

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
