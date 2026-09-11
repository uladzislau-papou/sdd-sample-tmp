# Use Case Specification – IssueIndividualLeasingContract

## Status
SPECIFIED

## Bounded Context
`individualleasing` — triggered via GraphQL mutation by an external client.

Cross-context. Owner: `individualleasing`. Callee: `masterleasing` (UC09).
Integration pattern: **synchronous inport call**, read-only, inside this use case's
transaction (`architecture.definition.md` § 11 rule 3).

## Purpose

Issue an **Einzel-Leasingvertrag (ELV)** for one employee and one bike under an
existing master leasing contract, with terms inherited from that contract and
validated against its commercial limits.


## 1. Intent

Turn an approved application (*Antragsnummer*, KAU) into a lease with fixed terms:
a monthly rate derived from the bike's leasing value, a term, a residual value, and
a service package.

The lease must be **consistent with the framework agreement it hangs from**. The
bike's price must fall inside the contract's band, the contract must be active, and
the lease cannot start before the contract did. Those three checks are what make
this more than a row insert.


## 2. Input Contract

Fields:
- `masterLeasingContractId` (ID) — required; the LRV this lease is issued under
- `elvNumber` (String) — required; must match `ELV-\d{8}` (I-01)
- `jobCyclistId` (String) — required, non-blank; the employee (*JobRadler:in*)
- `bikeId` (String) — required, non-blank; the leasing object (*Leasingobjekt*)
- `kauNumber` (String) — required, non-blank; the application number (*Antragsnummer*)
- `configuration`:
  - `servicePackage` (enum), `inheritanceMode` (enum)
  - `currency` (String)
  - `leasingValue` (decimal string)
  - `leasingFactorPercentage` (decimal string)
  - `serviceRate`, `insuranceRate`, `residualValue` (decimal strings)
  - `termMonths` (Int)
  - `termStart` (LocalDate)

Validation rules:
- `elvNumber` format (I-01)
- `termMonths` in `12..60` (I-09)
- all amounts non-negative and in one currency (I-10)
- `residualValue <= leasingValue` (I-11)
- `termEnd` and `ratePerMonth` are **derived, not supplied** — see below

**`ratePerMonth` and `termEnd` are computed, not accepted.**

```
ratePerMonth = leasingValue × leasingFactor,  HALF_UP, scale 4
termEnd      = termStart plus termMonths
```

Accepting them from the caller would let a client state a rate that does not follow
from the value and factor it also stated, and nothing downstream would notice
because all three would be persisted as given. They are computed by the driver,
asserted by the aggregate (I-02, I-03), and returned in the payload so the caller
can see what it got. The GraphQL input type has no field for either.

**No timestamp input.** `occurredAt` comes from `ClockPort`. `termStart` is a
business date the parties agreed and *is* an input
(`architecture.definition.md` § 8.1).

`employerId` and `lessorId` are **not** inputs either: they are copied from the
master contract's terms, because a lease whose employer differs from its contract's
employer is not a data-entry error to validate, it is a state that must be
unconstructible.


## 3. Output Contract

Return type:
- `individualLeasingContractId` (ID)
- `elvNumber` (String)
- `status` (String) — `"PENDING_ACTIVATION"` on success
- `ratePerMonth` (decimal string) — the derived value
- `termEnd` (LocalDate) — the derived value

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `MasterLeasingContractNotFoundException` (from UC09) | the referenced LRV does not exist | `NOT_FOUND` |
| `MasterContractNotActiveException` | the LRV is not `ACTIVE` (I-05) | `CONFLICT` |
| `PriceOutsideContractBandException` | `leasingValue` outside the LRV's price band (I-04) | `CONFLICT` |
| `TermStartBeforeMasterActivationException` | `termStart` before the LRV's activation date (I-06) | `CONFLICT` |
| `InvalidIndividualLeasingContractException` | any value-object or configuration invariant (I-01, I-02, I-03, I-09 to I-11) | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant, or malformed id | `BAD_REQUEST` |

The three inherited-term failures classify as `CONFLICT`, not `BAD_REQUEST`,
deliberately: the request was well-formed and was refused because of the *state of
another aggregate*. A client that gets `CONFLICT` here should look at the master
contract; one that gets `BAD_REQUEST` should look at its own input. Collapsing them
would make that unguessable.


## 4. Preconditions

- The master leasing contract exists and is `ACTIVE`
- Its price band admits `leasingValue`
- Its activation date is not after `termStart`

Deliberately **not** a precondition: that the credit limit has room, or that the
eligible-employee headcount has not been exhausted. See § 8.


## 5. Flow

1. Parse `masterLeasingContractId`; build every value object from the input
   (`ElvNumber`, `JobCyclistId`, `BikeId`, `KauNumber`, `Money`s, `LeasingFactor`,
   `LeaseTerm`) → each validates its own invariant
2. Compute `ratePerMonth` and `termEnd`
3. **Call `masterleasing`'s inport**: `ReadLeasingTermsUseCase.read(...)` (UC09) →
   throws `MasterLeasingContractNotFoundException` if absent
4. Map the returned `LeasingTermsResult` into this context's
   `InheritedLeasingTerms` — the anti-corruption step
5. Build `IlcConfiguration` → asserts I-02, I-03, I-10, I-11
6. Read `now` from `ClockPort`
7. `IndividualLeasingContract.issue(..., inheritedTerms, now)` → throws on I-04,
   I-05, I-06
8. Persist via `IndividualLeasingContractRepository.save(...)`
9. Publish `IndividualLeasingContractIssued` via `DomainEventPublisher`
10. Return the payload including the derived `ratePerMonth` and `termEnd`

**Step 1 precedes step 3 deliberately.** A malformed `elvNumber` is a
`BAD_REQUEST` without a cross-context call, so an obviously invalid request never
costs `masterleasing` a database round trip and never depends on that contract
existing.

**Step 4 is not ceremony.** `LeasingTermsResult` is `masterleasing`'s type;
`InheritedLeasingTerms` is this context's. The aggregate takes the local one, so a
change to the other context's result shape reaches exactly one mapping function
rather than the domain (ADR 0003).

**Step 7 is where the rules live, not step 4.** The driver fetches the band; the
aggregate decides whether the price is allowed
(`domain-vs-use-case.definition.md` § 4). Deleting the driver must not make a lease
issuable outside its band.


## 6. Side Effects

- Persistence: one row in `individual_leasing_contract`, one in
  `ilc_configuration`, written atomically. Added by
  `V2__DDL_create_individual_leasing_contract.sql`
- Cross-context read: one call to `masterleasing`'s inport. **Read-only** — it
  loads an aggregate and returns values, mutating nothing
- Event publication: `IndividualLeasingContractIssued`, after commit,
  context-local. `masterleasing` does not consume it and does not need to: it holds
  no lease count and enforces no invariant about leases (that aggregate's spec § 1)

### Why a synchronous call rather than a copy of the terms

The alternative is for `individualleasing` to hold its own copy of each contract's
band and status, refreshed by listening to `masterleasing`'s events. That is
cheaper at issue time and wrong in a specific way: the copy goes stale between the
amendment and the event, and a lease issued in that window is validated against
terms that no longer exist — silently, with no error anywhere.

The synchronous call costs availability coupling and buys the guarantee that the
band checked is the band in force. For a check that decides whether a contract may
exist, that is the right trade. It is recorded in `architecture.definition.md` § 11
rule 3 as the second sanctioned form.


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given an `ACTIVE` LRV activated `2026-01-01`, price band `1000.0000`–`5000.0000 EUR`
When a lease is issued with `leasingValue = 3000.0000 EUR`,
  `leasingFactorPercentage = 1.9000`, `termMonths = 36`, `termStart = 2026-02-01`
Then the lease is created with `status = PENDING_ACTIVATION`
And `ratePerMonth` is exactly `57.0000 EUR`
And `termEnd` is exactly `2029-02-01`
And `IndividualLeasingContractIssued` is published after commit

**AC-02 – Derived Values Are Not Caller-Supplied**
Given any valid issue request
When IssueIndividualLeasingContract is executed
Then `ratePerMonth` and `termEnd` are computed from the other inputs
And the GraphQL input type exposes no `ratePerMonth` or `termEnd` field

**AC-03 – Price Band Boundaries Are Inclusive**
Given a price band `1000.0000`–`5000.0000 EUR`
When leases are issued at exactly `1000.0000` and exactly `5000.0000`
Then both succeed
And a lease at `999.9999` and one at `5000.0001` are both refused with
  `PriceOutsideContractBandException`, classification `CONFLICT`

**AC-04 – Master Contract Not Active**
Given a LRV in `DRAFT`, and separately one in `CANCELLED`
When a lease is issued under it
Then `MasterContractNotActiveException` is raised with classification `CONFLICT`
  and nothing is persisted

**AC-05 – Term Start Before Master Activation**
Given a LRV activated `2026-04-01`
When a lease is issued with `termStart = 2026-03-31`
Then `TermStartBeforeMasterActivationException` is raised with classification `CONFLICT`
And a lease with `termStart = 2026-04-01` exactly is accepted

**AC-06 – Employer and Lessor Are Inherited, Not Supplied**
Given a LRV whose employer is `E-100`
When a lease is issued under it
Then the lease's `employerId` is `E-100`
And the GraphQL input type exposes no `employerId` or `lessorId` field

**AC-07 – Malformed ELV Number**
Given `elvNumber = "ELV-123"`
When IssueIndividualLeasingContract is executed
Then `InvalidIndividualLeasingContractException` is raised with classification
  `BAD_REQUEST` and **no cross-context call is made**

**AC-08 – Residual Value Exceeding Leasing Value**
Given `leasingValue = 3000.0000 EUR` and `residualValue = 3500.0000 EUR`
When IssueIndividualLeasingContract is executed
Then classification `BAD_REQUEST` is returned and nothing is persisted

**AC-09 – Master Contract Not Found**
Given no LRV exists for the given id
When IssueIndividualLeasingContract is executed
Then `MasterLeasingContractNotFoundException` is raised with classification `NOT_FOUND`


## 8. Failure Scenarios

| Scenario | Exception | Classification |
|----------|-----------|----------------|
| Master contract does not exist | `MasterLeasingContractNotFoundException` | `NOT_FOUND` |
| Master contract not `ACTIVE` | `MasterContractNotActiveException` | `CONFLICT` |
| Leasing value outside the price band | `PriceOutsideContractBandException` | `CONFLICT` |
| `termStart` before master activation | `TermStartBeforeMasterActivationException` | `CONFLICT` |
| Malformed ELV number, term out of range, residual exceeding value, mixed currency | `InvalidIndividualLeasingContractException` | `BAD_REQUEST` |
| `Money` / `Percentage` invariant, malformed id | `IllegalArgumentException` | `BAD_REQUEST` |

**Deliberately absent: the credit limit and the eligible-employee headcount.**

Both are in `MlcConfiguration` and both are real commercial limits, and neither is
enforced. They cannot be, from here: checking either means summing or counting the
leases already issued under the contract, which is a question about a *set* of
aggregates. Answering it inside this transaction would mean loading every sibling
lease — an unbounded read on the issue path — and enforcing it would make a
cross-aggregate invariant, which `modelling.definition.md` says is a modelling
error rather than a transaction-scoping question.

The honest design is a read-side projection maintaining exposure per contract, plus
a policy consulted here — the same shape as the inherited-term checks, with the
value passed in. That is a use case of its own and is not this one. Recorded here
rather than half-implemented, because a credit check that is approximately right is
worse than none: it would be trusted.

**Also absent: uniqueness of `elvNumber`, `bikeId` and `kauNumber`.** A unique
constraint on `elv_number` is in the schema and will surface as a database error
rather than a domain exception. See the port spec.


## 9. GraphQL Contract

Schema (`src/main/resources/graphql/individualleasing/individual-leasing-contract.graphqls`):

```graphql
input IlcConfigurationInput {
  servicePackage: ServicePackage!
  inheritanceMode: InheritanceMode!
  currency: String!
  leasingValue: String!
  leasingFactorPercentage: String!
  serviceRate: String!
  insuranceRate: String!
  residualValue: String!
  termMonths: Int!
  termStart: String!
}

input IssueIndividualLeasingContractInput {
  masterLeasingContractId: ID!
  elvNumber: String!
  jobCyclistId: String!
  bikeId: String!
  kauNumber: String!
  configuration: IlcConfigurationInput!
}

type IssueIndividualLeasingContractPayload {
  individualLeasingContractId: ID!
  elvNumber: String!
  status: String!
  ratePerMonth: String!
  termEnd: String!
}

type Mutation {
  issueIndividualLeasingContract(
    input: IssueIndividualLeasingContractInput!
  ): IssueIndividualLeasingContractPayload!
}
```

Note what the input type does **not** contain: `ratePerMonth`, `termEnd`,
`employerId`, `lessorId`, `version`, and any timestamp. Each omission is an
invariant expressed in the schema rather than validated in code — the strongest
place to put it, because a client cannot send what it cannot express (ADR 0008).

Operation:
```graphql
mutation IssueIndividualLeasingContract($input: IssueIndividualLeasingContractInput!) {
  issueIndividualLeasingContract(input: $input) {
    individualLeasingContractId
    elvNumber
    status
    ratePerMonth
    termEnd
  }
}
```

Response:
```json
{
  "data": {
    "issueIndividualLeasingContract": {
      "individualLeasingContractId": "9ab2...",
      "elvNumber": "ELV-00012345",
      "status": "PENDING_ACTIVATION",
      "ratePerMonth": "57.0000",
      "termEnd": "2029-02-01"
    }
  }
}
```

Error classification mapping:
- success – lease issued, pending activation
- `NOT_FOUND` – the referenced master contract does not exist
- `CONFLICT` – master contract not active, price outside band, term start too early
- `BAD_REQUEST` – any own-input invariant


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `IndividualLeasingContractTest.issue_createsContractPendingActivation`
      and `IlcConfigurationTest.ratePerMonth_isLeasingValueTimesFactor_atScaleFour`,
      which asserts the exact value `57.0000`
- [ ] AC-02 covered by `IssueIndividualLeasingContractDriverTest.issue_derivesRateAndTermEnd`
      and `IssueIndividualLeasingContractGraphQLControllerTest.schema_exposesNoDerivedFields`
- [ ] AC-03 covered by `IndividualLeasingContractTest.issue_acceptsPriceAtEitherBandBoundary`
      and `.issue_throwsPriceOutsideContractBandException_whenJustOutsideEitherEnd`
- [ ] AC-04 covered by `IndividualLeasingContractTest.issue_throwsMasterContractNotActiveException_whenMasterIsDraft`
      and `.whenMasterIsCancelled`
- [ ] AC-05 covered by `IndividualLeasingContractTest.issue_throwsTermStartBeforeMasterActivationException`
      and `.issue_acceptsTermStartEqualToMasterActivationDate`
- [ ] AC-06 covered by `IssueIndividualLeasingContractDriverTest.issue_copiesEmployerAndLessorFromTheMasterContract`
- [ ] AC-07 covered by `ElvNumberTest` and
      `IssueIndividualLeasingContractDriverTest.issue_validatesInputBeforeCallingTheMasterContextInport`
- [ ] AC-08 covered by `IlcConfigurationTest.throwsInvalidIndividualLeasingContractException_whenResidualExceedsLeasingValue`
- [ ] AC-09 covered by `IssueIndividualLeasingContractDriverTest.issue_propagatesNotFound_whenMasterContractIsAbsent`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `src/main/resources/graphql/individualleasing/individual-leasing-contract.graphqls` declares `issueIndividualLeasingContract`
- [ ] `graphql/uc05-issue-individual-leasing-contract.graphql` covers success, `NOT_FOUND`, all three `CONFLICT` causes, and `BAD_REQUEST`
- [ ] `InheritedLeasingTerms` is declared in `individualleasing.core.domain.individualleasingcontract`,
      and the aggregate depends on it rather than on `masterleasing`'s `LeasingTermsResult` —
      asserted structurally by `ContextRegistryTest`
- [ ] Only `IssueIndividualLeasingContractDriver` calls `masterleasing`'s inport —
      asserted by `ContextRegistryTest.individualLeasingReachesMasterLeasingInport_onlyFromADriver`
- [ ] Persistence roundtrip covered by `IndividualLeasingContractPersistenceAdapterIT`,
      including `rate_per_month` and `residual_value` at scale 4
- [ ] Flyway `V2__DDL_create_individual_leasing_contract.sql` exists, with a unique
      constraint on `elv_number`
- [ ] `documentation/ports/issue-individual-leasing-contract.inport.spec.md` and
      `documentation/ports/read-leasing-terms.inport.spec.md` reflect both sides

### Governance
- [ ] The unenforced credit limit and headcount (§ 8) are recorded as open items in
      `documentation/domain/aggregate-individual-leasing-contract.spec.md` § 7
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
