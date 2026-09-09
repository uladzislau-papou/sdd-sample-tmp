# Use Case Specification – AddContractToMaster

## Status
SPECIFIED

## Bounded Context
`contract` — triggered via GraphQL by external client.

## Purpose

Add one Contract to an existing Master.


## 1. Intent

**Source:** `n/a — invented scope. See `project.definition.md`, "Purpose of the service".`

**Business outcome:** a Master holds an agreement with a period and a monthly amount.

**This is the use case the aggregate boundary exists for.** All three aggregate-level
invariants — I-07, I-08 and I-09 in `aggregate-master.spec.md` § 2 — are enforced here, and
none of them can be checked by looking at the incoming contract alone. That is the whole
argument in `adr/0024`, and this specification is where it becomes testable rather than
asserted.


## 2. Input Contract

Fields:
- `masterId`
- `contractNumber`
- `startDate`
- `endDate`
- `monthlyAmount`
- `currency`

Validation rules:
- All six are required.
- `masterId` must parse as a UUID.
- `contractNumber` — I-03.
- `startDate`, `endDate` — ISO-8601 calendar dates (`LocalDate`); together they must satisfy
  I-04.
- `monthlyAmount` — a decimal string satisfying I-05.
- `currency` — I-06.

Not accepted on input:
- `contractId` — assigned by the driver.

**On dates and § 8.1.** `architecture.definition.md` § 8.1 forbids a GraphQL input type from
carrying a timestamp, and `TimestampRulesTest` enforces it against `Instant`, `OffsetDateTime`,
`ZonedDateTime` and `LocalDateTime`. `startDate` and `endDate` are `LocalDate` and are
therefore outside that rule — **by substance, not by loophole**. § 8.1's subject is a client
asserting a fact about *our clock*: when something happened. A contract's start and end are
terms the parties agreed, and they are as much business data as the amount. If they were
`Instant`, the rule would apply and the test would catch it.

**On `monthlyAmount` as a string.** It crosses the wire as a decimal string, for the reason
given in `uc02-get-master.spec.md` § 9: GraphQL has no decimal scalar and a `Float` would make
I-05's two-decimal rule unenforceable at the boundary.


## 3. Output Contract

Return type:
- `AddContractToMasterResult` carrying `masterId`, `contractId`, `contractNumber` and the
  Master's resulting `contractCount`.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `MasterNotFoundException` | No Master with that id | `404` |
| `MasterNotActiveException` | I-09 — the Master is `INACTIVE` | `409` |
| `DuplicateContractNumberException` | I-07 — the number is already used in this Master | `409` |
| `ContractLimitExceededException` | I-08 — the Master already holds 50 contracts | `409` |
| `InvalidContractException` | I-03, I-04, I-05 or I-06 violated | `400` |

No REST endpoint exists (`adr/0020`); the column records what the statuses would be, and it is
worth noticing that three of them are `409` while GraphQL has no conflict classification —
see § 9.


## 4. Preconditions

- The Master must exist.
- The Master must be `ACTIVE` (I-09).
- The Master must hold fewer than 50 contracts (I-08).
- No contract in this Master may already carry the given number (I-07).


## 5. Flow

1. Driver parses `masterId` and builds the value objects: `ContractNumber`, `ContractPeriod`,
   `Money`. Any of these may raise `InvalidContractException` before the Master is loaded.
2. Driver generates a `ContractId`.
3. Driver calls `MasterRepository.findById`; raises `MasterNotFoundException` if empty.
4. Driver calls `master.addContract(contractId, contractNumber, period, monthlyAmount)`, which
   checks I-09, then I-07, then I-08, in that order.
5. Driver calls `MasterRepository.save(master)`.
6. Driver publishes `master.pullDomainEvents()` inside the transaction.
7. Driver maps to `AddContractToMasterResult`.

Steps 3–6 are one transaction.

**Value objects are built before the Master is loaded (step 1).** A malformed amount is a bad
request whatever the Master's state, and validating it first means the caller gets the error
that is actually about their input rather than a `NOT_FOUND` for an unrelated typo in the id.

**The precedence between I-09, I-07 and I-08 is fixed, not incidental.** A request that is
simultaneously against an inactive Master, using a duplicate number, and over the limit raises
`MasterNotActiveException`. Without a stated order, two correct-looking implementations return
different errors for the same request and the test that pins it down is written after the
first client complains. `aggregate-master.spec.md` § 4 owns the order; AC-07 asserts it.


## 6. Side Effects

- **Persistence:** one new `contract` row. The `master` row itself is unchanged.
- **External calls:** none.
- **Event publication:** `ContractAddedToMaster`.


## 7. Acceptance Criteria

**AC-01 – A valid contract is added**
Given an `ACTIVE` Master holding no contracts
When `AddContractToMaster` is called with a valid contract
Then the contract is persisted under that Master and `contractCount` is `1`

**AC-02 – Adding publishes ContractAddedToMaster**
Given an `ACTIVE` Master
When a contract is added
Then exactly one `ContractAddedToMaster` is published, carrying both the master and contract ids

**AC-03 – A duplicate contract number within one Master is rejected**
Given an `ACTIVE` Master already holding contract `C-0001`
When `AddContractToMaster` is called with contract number `C-0001`
Then `DuplicateContractNumberException` is raised and nothing is persisted

**AC-04 – The same contract number is accepted under a different Master**
Given Master A holds contract `C-0001` and Master B holds none
When `AddContractToMaster` is called on Master B with contract number `C-0001`
Then it succeeds

AC-03 and AC-04 are a pair and neither means anything alone. Together they say the uniqueness
is scoped to the aggregate — which is the difference between an invariant this service can
actually hold and a global constraint it cannot (`aggregate-master.spec.md` § 2).

**AC-05 – A contract cannot be added to an inactive Master**
Given an `INACTIVE` Master
When `AddContractToMaster` is called
Then `MasterNotActiveException` is raised and nothing is persisted

**AC-06 – The fifty-first contract is rejected**
Given an `ACTIVE` Master holding 50 contracts
When `AddContractToMaster` is called
Then `ContractLimitExceededException` is raised and nothing is persisted

**AC-07 – Invariants are checked in the specified order**
Given an `INACTIVE` Master holding 50 contracts including `C-0001`
When `AddContractToMaster` is called with contract number `C-0001`
Then `MasterNotActiveException` is raised — not the duplicate or limit exception

**AC-08 – An end date on or before the start date is rejected**
Given any request
When `endDate` is equal to or earlier than `startDate`
Then `InvalidContractException` is raised

**AC-09 – A non-positive or over-precise amount is rejected**
Given any request
When `monthlyAmount` is zero, negative, or has more than two decimal places
Then `InvalidContractException` is raised

**AC-10 – An unknown Master is not found**
Given no Master exists with a given id
When `AddContractToMaster` is called with it
Then `MasterNotFoundException` is raised

**AC-11 – Periods may overlap**
Given an `ACTIVE` Master holding a contract running 2026-01-01 to 2026-12-31
When a second contract running 2026-06-01 to 2027-05-31 is added
Then it succeeds

AC-11 asserts an absence of a rule, like UC01's AC-05. Non-overlapping periods is the first
constraint a reader invents for this model; `project.definition.md` lists it as out of scope,
and without this criterion someone adds it later believing they are fixing a gap.


## 8. Failure Scenarios

- Master not found (AC-10).
- Master inactive (AC-05).
- Duplicate contract number in this Master (AC-03).
- Contract limit reached (AC-06).
- Invalid contract number, period, amount or currency (AC-08, AC-09).
- Repository failure — transaction rolls back and no contract row is written.


## 9. API Contract

### REST

Not applicable — `adr/0020-graphql-as-the-only-transport.adr.md`.

### GraphQL

Operation:
```graphql
mutation addContractToMaster(input: AddContractToMasterInput!): AddContractToMasterPayload!
```

```graphql
input AddContractToMasterInput {
    masterId: ID!
    contractNumber: String!
    "ISO-8601 calendar date, e.g. 2026-06-15."
    startDate: String!
    "ISO-8601 calendar date, e.g. 2027-06-14."
    endDate: String!
    "Decimal string, at most two decimal places, e.g. \"149.50\"."
    monthlyAmount: String!
    "ISO-4217 shape: three uppercase letters."
    currency: String!
}

type AddContractToMasterPayload {
    masterId: ID!
    contractId: ID!
    contractNumber: String!
    contractCount: Int!
}
```

Error classification:
- `BAD_REQUEST` – `InvalidContractException`, a malformed id, **and all three conflict
  conditions**: `MasterNotActiveException`, `DuplicateContractNumberException`,
  `ContractLimitExceededException`
- `NOT_FOUND` – `MasterNotFoundException`
- `INTERNAL_ERROR` – persistence failure

**This is where the vocabulary asymmetry actually costs something, so it is stated plainly.**
Over HTTP these would be four `400`s and three `409`s — seven conditions across two statuses.
GraphQL has no conflict classification, so all seven collapse into `BAD_REQUEST`, and a client
cannot tell "your input is malformed" from "your input is fine but the Master's state
forbids it". The first is worth retrying after the user edits the form; the second is not.

A client that needs the distinction needs an error-code field in the payload. That is a real
extension and it is deliberately **not** invented here — inventing an error taxonomy nobody
has asked for is how a contract acquires a vocabulary no client uses. The asymmetry is
documented so that whoever hits it finds it written down rather than discovering it in
support.

### Executable requests

`api/uc05-add-contract-to-master.graphql` covers the happy path and one request per condition
listed above — including all three conflict conditions separately, even though they share a
classification. The point of the file is to make each *condition* executable, not each
classification.


## 10. Definition of Done

Citations name test **classes** rather than methods, for the reason set out in
`uc01-create-master.spec.md` § 10.

### Behaviour
- [ ] AC-01 covered by `AddContractToMasterDriverTest`
- [ ] AC-02 covered by `AddContractToMasterDriverTest`
- [ ] AC-03 covered by `MasterTest`
- [ ] AC-04 covered by `MasterJpaRepositoryIT` — two Masters, one contract number
- [ ] AC-05 covered by `MasterTest`
- [ ] AC-06 covered by `MasterTest`
- [ ] AC-07 covered by `MasterTest` — the precedence test named in `aggregate-master.spec.md` § 7
- [ ] AC-08 covered by `ContractPeriodTest`
- [ ] AC-09 covered by `MoneyTest`
- [ ] AC-10 covered by `AddContractToMasterDriverTest`
- [ ] AC-11 covered by `MasterTest`
- [ ] `ContractNumber` and `CurrencyCode` invariants covered by `ContractNumberTest` and
      `CurrencyCodeTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `api/uc05-add-contract-to-master.http` — not applicable, § 9 says REST is not
- [ ] `api/uc05-add-contract-to-master.graphql` covers every **condition** in § 9, not merely
      every classification
- [ ] Persistence roundtrip of a Master with contracts covered by `MasterJpaRepositoryIT`
- [ ] `documentation/ports/add-contract-to-master.inport.spec.md` reflects the port as implemented

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an artifact
- [ ] Quality gates green (`test.definition.md` § 7)
