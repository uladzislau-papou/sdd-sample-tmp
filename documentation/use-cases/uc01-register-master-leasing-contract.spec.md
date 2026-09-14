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
- The command carries **one** `currency` field, applied to every amount
  (`register-master-leasing-contract.inport.spec.md` § 2.1) — a credit-limit-vs-band
  mismatch is therefore not reachable through this input contract; only
  `priceRangeMin` vs `priceRangeMax` could ever differ, and only if a future input
  shape ever gave them independent currencies. `PriceRange` still guards this
  defensively at the value-object level (Always-Valid holds for direct domain
  construction too, not just for command-driven construction), which is what
  `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenCurrenciesDiffer`
  exercises
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
Given a `PriceRange` whose `min` and `max` are constructed with different
currencies (a value-object-level guard — see § 2's validation-rules note on why a
credit-limit-vs-band mismatch is not reachable through this use case's input)
When the value object is constructed
Then `InvalidMasterLeasingContractException` is raised with classification
  `BAD_REQUEST`

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
| `currency` is not a valid ISO-4217 code | `InvalidMasterLeasingContractException` | `BAD_REQUEST` |

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

**The schema file also declares a placeholder `Query` root type**, not shown below
because it is not part of this use case's contract:

```graphql
type Query {
  _placeholder: Boolean
}
```

This is the first schema file in the application, and GraphQL requires a `Query`
root operation type to exist even when — as here — the only operation being added
is a mutation. The placeholder is temporary: it is removed the moment a real
read-side use case exists to populate `Query` (UC09 `ReadLeasingTerms` is the first
candidate, per `architecture.definition.md` § 5).

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
- [x] AC-01 covered by `MasterLeasingContractTest.register_createsContractInDraft`
      and `RegisterMasterLeasingContractDriverTest.register_savesAndPublishes`
- [x] AC-02 covered by `MasterLeasingContractTest.register_storesParentReference`
- [x] AC-03 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenParentIsSelf`
- [x] AC-04 covered by `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenMinExceedsMax`
      and `RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenPriceBandInverted`
- [x] AC-05 covered by `PriceRangeTest.throwsInvalidMasterLeasingContractException_whenCurrenciesDiffer`
- [x] AC-06 covered by `NoticePeriodTest` (boundary cases at 0, 1, 36, 37)
- [x] AC-07 covered by `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenInitialVersionIsNotOne`
      and `RegisterMasterLeasingContractGraphQLControllerTest.schema_exposesNoVersionField`
- [x] Domain invariants for `MasterLeasingContract` covered by `MasterLeasingContractTest`
- [x] Driver orchestration and event emission covered by `RegisterMasterLeasingContractDriverTest`
- [x] Every failure scenario in § 8 has a negative test — self-parent:
      `MasterLeasingContractTest.register_throwsInvalidMasterLeasingContractException_whenParentIsSelf`;
      VO invariant, every value object in the configuration: `PriceRangeTest`
      (I-09, all three clauses), `NoticePeriodTest` (I-12), `PartnerNumberTest`
      (I-01), `CreditLimitTest` (I-10), `EligibleEmployeesTest` (I-11),
      `ReturnQuotaTest` (I-13), `ConfigurationVersionTest`, `CancellationReasonTest`
      (I-14); Money/Percentage invariant:
      `MoneyTest.compareTo_throwsIllegalArgumentException_whenCurrenciesDiffer`,
      `EmployerIdTest`, `LessorIdTest`; malformed ID:
      `RegisterMasterLeasingContractDriverTest.register_throwsIllegalArgumentException_whenParentIdIsMalformed`
      (also asserts nothing is persisted or published); currency is not a valid
      ISO-4217 code:
      `RegisterMasterLeasingContractDriverTest.register_throwsInvalidMasterLeasingContractException_whenCurrencyIsNotIso4217`
      (also GraphQL-level: `RegisterMasterLeasingContractGraphQLControllerTest.register_returnsBadRequest_whenIllegalArgumentExceptionThrown`
      covers the `IllegalArgumentException → BAD_REQUEST` resolver mapping this
      and the malformed-ID/Money-Percentage rows both rely on)

### Contracts
- [x] `src/main/resources/graphql/masterleasing/master-leasing-contract.graphqls` declares `registerMasterLeasingContract`
- [x] `graphql/uc01-register-master-leasing-contract.graphql` covers success and `BAD_REQUEST`
- [ ] Persistence roundtrip covered by `MasterLeasingContractPersistenceAdapterIT`,
      including that every monetary column round-trips at scale 4
- [ ] Flyway `V1__DDL_create_master_leasing_contract.sql` exists
- [x] `documentation/ports/master-leasing-contract-repository.outport.spec.md` reflects `save`
- [x] `documentation/ports/register-master-leasing-contract.inport.spec.md` reflects the inport

### Governance
- [x] This spec reconciled against the code by `spec-documenter`
- [x] `ddd-hex-reviewer` returns `PASS` — reached on the seventh pass, after six
      DRIFT rounds across the increment's review history (see `tasks.md`
      "DoD Scoreboard – UC01" for the finding-by-finding history)
- [ ] Quality gates green (`test.definition.md` § 7) — awaits a verified
      `./gradlew build` run; blocked structurally until the persistence roundtrip
      and Flyway migration boxes above are closed, since the gate is all-or-nothing
