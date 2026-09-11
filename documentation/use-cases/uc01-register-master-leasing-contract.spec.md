# Use Case Specification – RegisterMasterLeasingContract

## Status
SPECIFIED

## Bounded Context
`masterleasing` — triggered via GraphQL mutation by an external client (an
operations user in the internal admin surface).

## Purpose

Record a newly negotiated **Leasing-Rahmenvertrag (LRV)** and its initial
commercial terms, in `DRAFT`, before it is countersigned and activated.


## 1. Intent

Bring a framework agreement into the system as a first-class object with validated
commercial terms, so that everything issued beneath it later has something
authoritative to inherit from.

Registering does not make the contract effective. That is UC02, and the separation
is deliberate: terms are negotiated and corrected repeatedly before signature, and
a `DRAFT` contract can be amended (UC03) without any lease depending on it.


## 2. Input Contract

Fields:
- `employerId` (String) — the Employer (*Arbeitgeber*); required, non-blank
- `lessorId` (String) — the Lessor (*Leasinggeber*); required, non-blank
- `partnerNumber` (String) — the *Partnernummer*, Odoo ↔ Radar join key; required, non-blank
- `parentMasterLeasingContractId` (ID) — optional; the base contract when this one
  is an affiliated contract for a company in the same corporate group
- `configuration` — the initial `MLC_CONFIGURATION`:
  - `contractType` (enum), `salesChannel` (enum), `inheritanceMode` (enum)
  - `creditLimitAmount` (BigDecimal) + `currency` (String)
  - `priceRangeMin`, `priceRangeMax` (BigDecimal)
  - `eligibleEmployees` (Int)
  - `jointLiability` (Boolean) — *gesamtschuldnerische Haftung*
  - `returnQuotaPercentage` (BigDecimal)
  - `noticePeriodMonths` (Int)

Validation rules:
- All monetary amounts share one `currency`; mixed currencies are rejected by
  `Money` and `PriceRange`
- `priceRangeMin <= priceRangeMax` (I-09)
- `creditLimitAmount >= 0` (I-10), `eligibleEmployees >= 0` (I-11),
  `noticePeriodMonths` in `1..36` (I-12), `returnQuotaPercentage` in `0..100` (I-13)
- `parentMasterLeasingContractId`, when given, must not equal the id being created (I-02)

**The configuration version is not an input.** The initial configuration is version
1 by definition (I-03); accepting a version from the client would let a caller start
a contract at version 7 and make the amendment sequence meaningless. The `MlcConfigurationInput`
type has no `version` field.

**There is no timestamp input.** `occurredAt` comes from `ClockPort`
(`architecture.definition.md` § 8.1). Note that `activationDate` is *not* an input
here either — it belongs to UC02.


## 3. Output Contract

Return type:
- `masterLeasingContractId` (ID)
- `status` (String) — always `"DRAFT"` on success
- `configurationVersion` (Int) — always `1` on success

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `InvalidMasterLeasingContractException` | any value-object or creation invariant violated (I-01, I-02, I-03, I-09 to I-14) | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant violated, or a malformed `ID` argument | `BAD_REQUEST` |

There are no HTTP status codes; the classification is carried in
`extensions.classification` (`architecture.definition.md` § 4.5).

**There is no `NOT_FOUND` for `parentMasterLeasingContractId`.** The parent is not
loaded and its existence is not checked — see § 8.


## 4. Preconditions

None. This use case creates an aggregate; nothing needs to exist first.

Specifically **not** a precondition: that the employer, the lessor or the parent
contract exists. See § 8.


## 5. Flow

1. Generate `MasterLeasingContractId`
2. Build the value objects from the input — `Money`, `PriceRange`, `CreditLimit`,
   `EligibleEmployees`, `NoticePeriod`, `ReturnQuota`, `PartnerNumber`,
   `EmployerId`, `LessorId` — each validating its own invariant
3. Build `MlcConfiguration` at version 1
4. Read `now` from `ClockPort`
5. `MasterLeasingContract.register(...)` → throws on I-02 or I-03
6. Persist via `MasterLeasingContractRepository.save(...)`
7. Publish `MasterLeasingContractRegistered` via `DomainEventPublisher`
8. Return `{ masterLeasingContractId, status: "DRAFT", configurationVersion: 1 }`

Ordering note: **every value object is constructed before the aggregate is**, so an
invalid credit limit is a `BAD_REQUEST` with no database round trip. Validation
never depends on persistence state, because none of it can.


## 6. Side Effects

- Persistence: one row in `master_leasing_contract`, one in `mlc_configuration`,
  written atomically in the driver's transaction. Added by
  `V1__DDL_create_master_leasing_contract.sql`
- Event publication: `MasterLeasingContractRegistered`, after commit (ADR 0002),
  **context-local** — no other context consumes it


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given a valid employer, lessor, partner number and configuration
When RegisterMasterLeasingContract is executed
Then a contract is created with `status = DRAFT`, `configurationVersion = 1`,
  and `activationDate`, `cancelledDate` and `cancellationReason` all absent
And `MasterLeasingContractRegistered` is published after commit

**AC-02 – Affiliated Contract**
Given a `parentMasterLeasingContractId` different from the contract being created
When RegisterMasterLeasingContract is executed
Then the contract is created and carries that parent reference

**AC-03 – Self-Parenting Rejected**
Given a `parentMasterLeasingContractId` equal to the new contract's own id
When RegisterMasterLeasingContract is executed
Then `InvalidMasterLeasingContractException` is raised and nothing is persisted

**AC-04 – Inverted Price Band Rejected**
Given `priceRangeMin = 3000.0000 EUR` and `priceRangeMax = 1000.0000 EUR`
When RegisterMasterLeasingContract is executed
Then `InvalidMasterLeasingContractException` is raised with classification
  `BAD_REQUEST` and nothing is persisted

**AC-05 – Mixed Currency Rejected**
Given a credit limit in `EUR` and a price band in `CHF`
When RegisterMasterLeasingContract is executed
Then the request is rejected with classification `BAD_REQUEST`

**AC-06 – Notice Period Bounds**
Given `noticePeriodMonths = 0`, and separately `noticePeriodMonths = 37`
When RegisterMasterLeasingContract is executed
Then each is rejected, and `noticePeriodMonths = 1` and `= 36` are accepted

**AC-07 – Version Is Not Caller-Supplied**
Given any valid input
When RegisterMasterLeasingContract is executed
Then the stored configuration version is `1`
And the GraphQL input type exposes no `version` field


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Contract is its own parent | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |
| Any value-object invariant violated | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |
| `Money` / `Percentage` invariant violated | `IllegalArgumentException` | `BAD_REQUEST` |
| Malformed `ID` argument | `IllegalArgumentException` | `BAD_REQUEST` |

**Deliberately absent: referential checks.** The employer, the lessor and the parent
contract are not verified to exist.

For `employerId` and `lessorId` that is forced by the context boundary — both name
records in external systems this service does not read (ADR 0005 category 3), and
inventing a lookup would mean an outbound port to a directory that is out of scope.

For `parentMasterLeasingContractId` it is a choice, and a weaker one. The parent
*is* an aggregate in this context and could be loaded. It is not, because loading it
would only prove it existed at registration time and would still not prevent the
cycle that matters (A→B→A), which spans aggregates and cannot be checked from inside
one. Recorded as an open gap in `documentation/notes.md` rather than half-solved.


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls`):

```graphql
input MlcConfigurationInput {
  contractType: ContractType!
  salesChannel: SalesChannel!
  inheritanceMode: InheritanceMode!
  currency: String!
  creditLimitAmount: String!
  priceRangeMin: String!
  priceRangeMax: String!
  eligibleEmployees: Int!
  jointLiability: Boolean!
  returnQuotaPercentage: String!
  noticePeriodMonths: Int!
}

input RegisterMasterLeasingContractInput {
  employerId: String!
  lessorId: String!
  partnerNumber: String!
  parentMasterLeasingContractId: ID
  configuration: MlcConfigurationInput!
}

type RegisterMasterLeasingContractPayload {
  masterLeasingContractId: ID!
  status: String!
  configurationVersion: Int!
}

type Mutation {
  registerMasterLeasingContract(
    input: RegisterMasterLeasingContractInput!
  ): RegisterMasterLeasingContractPayload!
}
```

**Monetary and percentage values are `String!`, not `Float`.** GraphQL's `Float` is
an IEEE-754 double, which cannot represent `0.01` exactly — passing a leasing amount
through it reintroduces precisely the defect `Money` exists to prevent
(`modelling.definition.md`, Money). A decimal string is parsed to `BigDecimal`
without loss. A custom `Decimal` scalar would be tidier and is deferred; it is a
schema addition, not a behaviour change.

Operation:
```graphql
mutation RegisterMasterLeasingContract($input: RegisterMasterLeasingContractInput!) {
  registerMasterLeasingContract(input: $input) {
    masterLeasingContractId
    status
    configurationVersion
  }
}
```

Response:
```json
{
  "data": {
    "registerMasterLeasingContract": {
      "masterLeasingContractId": "3f1c...",
      "status": "DRAFT",
      "configurationVersion": 1
    }
  }
}
```

Error classification mapping:
- success – contract registered in `DRAFT`
- `BAD_REQUEST` – any invariant violation in § 8

Every classification listed here MUST have a matching operation in
`graphql/uc01-register-master-leasing-contract.graphql`.


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `MasterLeasingContractTest.register_createsContractInDraft`
      and `RegisterMasterLeasingContractDriverTest.register_savesAndPublishes`
- [ ] AC-02 covered by `MasterLeasingContractTest.register_storesParentReference`
- [ ] AC-03 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenParentIsSelf`
- [ ] AC-04 covered by `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenMinExceedsMax`
      and `RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenPriceBandInverted`
- [ ] AC-05 covered by `PriceRangeTest.throwsIllegalArgumentException_whenCurrenciesDiffer`
- [ ] AC-06 covered by `NoticePeriodTest` (boundary cases at 0, 1, 36, 37)
- [ ] AC-07 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenInitialVersionIsNotOne`
      and `RegisterMasterLeasingContractGraphQLControllerTest.schema_exposesNoVersionField`
- [ ] Domain invariants for `MasterLeasingContract` covered by `MasterLeasingContractTest`
- [ ] Driver orchestration and event emission covered by `RegisterMasterLeasingContractDriverTest`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `registerMasterLeasingContract`
- [ ] `graphql/uc01-register-master-leasing-contract.graphql` covers success and `BAD_REQUEST`
- [ ] Persistence roundtrip covered by `MasterLeasingContractPersistenceAdapterIT`,
      including that every monetary column round-trips at scale 4
- [ ] Flyway `V1__DDL_create_master_leasing_contract.sql` exists
- [ ] `documentation/ports/master-leasing-contract-repository.outport.spec.md` reflects `save`
- [ ] `documentation/ports/register-master-leasing-contract.inport.spec.md` reflects the inport

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
