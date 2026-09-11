# Use Case Specification – ReadLeasingTerms

## Status
SPECIFIED

## Bounded Context
`masterleasing` — triggered by a **synchronous inport call** from
`individualleasing`'s UC05 driver. There is no GraphQL surface.

Cross-context. Owner: `masterleasing`. Caller: `individualleasing`.
Integration pattern: **synchronous inport call**, read-only
(`architecture.definition.md` § 11 rule 3).

## Purpose

Publish the commercial terms of a **LRV** that an **ELV** issued under it must
inherit and be validated against.


## 1. Intent

`individualleasing` needs four things when issuing a lease: whether the master
contract is active, its price band, its activation date, and the employer and lessor
the lease belongs to. It needs the **values**, not the model.

This use case is the published boundary that supplies them. It is the reason ADR
0003's context split survives contact with term inheritance: what crosses is a flat
result of shared and primitive types, never an `MlcConfiguration` and never the
aggregate.


## 2. Input Contract

Not a client-facing operation. `ReadLeasingTermsQuery` carries:

- `masterLeasingContractId` (String) — the contract to read

A `String` rather than `MasterLeasingContractId` because the caller is
`individualleasing`, which holds the identity as a plain `String` (ADR 0005
category 2). This driver parses it into the typed form — the owning context is the
one entitled to do that.

No timestamp, no clock. The use case is read-only and observes nothing.


## 3. Output Contract

Return type: `LeasingTermsResult`

| Field | Type | Why the caller needs it |
|-------|------|------------------------|
| `masterLeasingContractId` | `String` | correlation |
| `active` | `Boolean` | UC05 I-05: a lease may only be issued under an active contract |
| `activationDate` | `LocalDate?` | UC05 I-06: `termStart` may not precede it. Null while the contract is `DRAFT` |
| `priceRangeMin` | `Money` | UC05 I-04 |
| `priceRangeMax` | `Money` | UC05 I-04 |
| `employerId` | `EmployerId` | copied onto the lease; not a caller input (UC05 § 2) |
| `lessorId` | `LessorId` | likewise |
| `configurationVersion` | `Int` | records which version's terms were inherited |

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `MasterLeasingContractNotFoundException` | no contract with the given id | Not applicable — surfaces through UC05 as `NOT_FOUND` |
| `IllegalArgumentException` | the supplied id is not a well-formed UUID | surfaces through UC05 as `BAD_REQUEST` |

### What this result deliberately does not contain

`creditLimit`, `eligibleEmployees`, `returnQuota`, `noticePeriod`, `contractType`,
`salesChannel`, `jointLiability`.

They are all real inherited terms and none of them is here, because **nothing
consumes them**. UC05 cannot enforce the credit limit or the headcount from inside
one transaction — both need a count across sibling leases, which is a read-side
question (UC05 § 8) — and the rest are informational.

Returning them anyway would be the common failure of a published API: a result type
shaped like the source aggregate rather than like the consumer's need, which then
has to be maintained, versioned and reasoned about forever. `architecture.definition.md`
§ 4.2 asks inport types to be "small and intention-revealing"; this is what that
costs to honour.

`active` is a `Boolean` rather than the `MasterLeasingContractStatus` enum for the
same reason. The caller asks one question — may I issue? — and does not need to know
that `DRAFT`, `CANCELLED` and `ENDED` are three different ways of saying no. Exposing
the enum would put this context's state model in the other context's compile-time
dependencies, which is exactly what ADR 0003 avoided for identities.

**`Money`, `EmployerId` and `LessorId` in the signature are not a leak.** All three
live in `shared.domain`, which both contexts may depend on
(`architecture.definition.md` § 9). No `masterleasing`-owned type appears.


## 4. Preconditions

- The contract exists

No state precondition. A `DRAFT` or `CANCELLED` contract returns successfully with
`active = false`. **Refusing to read a non-active contract would be wrong**: the
"you cannot issue under this" decision belongs to the caller's aggregate (I-05), not
to this query, and turning it into a `NOT_FOUND`-shaped failure here would make the
two indistinguishable to UC05.


## 5. Flow

1. Parse `MasterLeasingContractId` from the supplied `String`
2. Load via `MasterLeasingContractRepository.findById(...)` → throw
   `MasterLeasingContractNotFoundException` if absent
3. Map the aggregate to `LeasingTermsResult`
4. Return it

No mutation, no event, no clock.

**This loads the aggregate rather than using a read-side projection**, and that is
deliberate despite § 5 of `architecture.definition.md` pointing queries at
`outbound.persistence.read`. The discriminator that section gives is whether the
answer must respect invariant-checked state: here it must, because another context
is about to create a contract on the strength of it. An eventually-consistent
projection would let a lease be issued against a price band that was amended a
moment ago — silently, which is the failure mode UC05 § 6 rejects the cached-copy
design for.

The cost is an aggregate load on the lease-issue path. Accepted.


## 6. Side Effects

None. This use case is read-only: it persists nothing, publishes nothing, and calls
no outbound port other than the repository.

It runs inside UC05's transaction because it is called from within it, which gives
the caller a consistent read. It does not open one of its own.


## 7. Acceptance Criteria

**AC-01 – Active Contract**
Given an `ACTIVE` contract activated `2026-01-01` with band `1000.0000`–`5000.0000 EUR`
When ReadLeasingTerms is executed
Then the result carries `active = true`, that activation date, both band values at
  scale 4, the employer and lessor, and the current configuration version

**AC-02 – Draft Contract Is Readable**
Given a `DRAFT` contract
When ReadLeasingTerms is executed
Then the call succeeds with `active = false` and `activationDate = null`

**AC-03 – Cancelled Contract Is Readable**
Given a `CANCELLED` contract
When ReadLeasingTerms is executed
Then the call succeeds with `active = false` and the activation date it had

**AC-04 – Contract Not Found**
Given no contract exists for the given id
When ReadLeasingTerms is executed
Then `MasterLeasingContractNotFoundException` is raised

**AC-05 – The Result Exposes No Owned Type**
Given any contract
When ReadLeasingTerms is executed
Then `LeasingTermsResult` contains no `masterleasing`-owned type — no
  `MasterLeasingContractId`, no `MlcConfiguration`, no `MasterLeasingContractStatus`,
  no `PriceRange`, no `CreditLimit`

**AC-06 – Reading Mutates Nothing**
Given any contract
When ReadLeasingTerms is executed
Then no repository write occurs and no domain event is published

**AC-07 – Terms Reflect the Current Configuration Version**
Given a contract amended from version 2 to version 3 with a wider band
When ReadLeasingTerms is executed
Then the band returned is version 3's, and `configurationVersion` is `3`


## 8. Failure Scenarios

| Scenario | Exception | Effect |
|----------|-----------|--------|
| Contract does not exist | `MasterLeasingContractNotFoundException` | propagates to UC05, classified `NOT_FOUND` |
| Malformed id string | `IllegalArgumentException` | propagates to UC05, classified `BAD_REQUEST` |

Notably **absent**: a failure for a non-active contract (§ 4).

**Availability coupling is the real failure mode and it has no entry above**, because
it is not an exception this use case raises. If `masterleasing` is unavailable, UC05
cannot issue a lease. ADR 0003 accepts that, and UC05 § 6 states why the alternative —
a cached copy of the terms in `individualleasing` — is worse: it fails silently
against stale terms instead of loudly against an unavailable dependency.


## 9. GraphQL Contract

Not applicable — inport-triggered, and deliberately so.

This use case would make a natural GraphQL `query`, and exposing it is a real option
that is being declined for now. There is no client asking for it, and publishing it
would turn an internal cross-context contract into an external one — after which
narrowing `LeasingTermsResult` (§ 3) becomes a breaking schema change and an ADR
trigger (`sdd.playbook.md` § 6 item 12) rather than a refactor.

If a read surface for master leasing contracts is wanted, the right shape is a
purpose-built query backed by `outbound.persistence.read`, not this inport widened
into one (`documentation/notes.md`, Missing read side).

`spec-documenter` must not create a `graphql/uc09-*.graphql` file.


## 10. Definition of Done

### Behaviour
- [ ] AC-01 covered by `ReadLeasingTermsDriverTest.read_returnsTheCurrentTerms`
- [ ] AC-02 covered by `ReadLeasingTermsDriverTest.read_returnsInactive_forADraftContract`
- [ ] AC-03 covered by `ReadLeasingTermsDriverTest.read_returnsInactive_forACancelledContract`
- [ ] AC-04 covered by `ReadLeasingTermsDriverTest.read_throwsNotFound_whenContractIsAbsent`
- [ ] AC-05 covered by `ContextRegistryTest.individualLeasing_doesNotImportMasterLeasingInternals`,
      which fails if any owned type reaches the caller through this result
- [ ] AC-06 covered by `ReadLeasingTermsDriverTest.read_writesNothingAndPublishesNothing`
- [ ] AC-07 covered by `ReadLeasingTermsDriverTest.read_reflectsTheCurrentConfigurationVersion`
- [ ] Every failure scenario in § 8 has a negative test

### Contracts
- [ ] `ReadLeasingTermsUseCase` exists in `masterleasing.core.inport.usecase` and is
      framework-free
- [ ] `LeasingTermsResult` exists in `masterleasing.core.inport.result` and contains
      only `shared.domain` and primitive types (AC-05)
- [ ] Only `IssueIndividualLeasingContractDriver` calls it — asserted by
      `ContextRegistryTest.individualLeasingReachesMasterLeasingInport_onlyFromADriver`
- [ ] `documentation/ports/read-leasing-terms.inport.spec.md` reflects the inport,
      including why the result is narrower than the configuration
- [ ] **No `graphql/uc09-*.graphql` file exists**, per § 9

### Governance
- [ ] The decision not to expose this as a GraphQL query (§ 9) is recorded here, so
      its absence is a decision rather than an omission
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
