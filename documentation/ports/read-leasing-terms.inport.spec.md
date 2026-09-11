# Port Specification – ReadLeasingTermsUseCase (Inport)

## Purpose

The inbound application boundary through which `individualleasing` obtains the
commercial terms a lease inherits from its master leasing contract.

**This is a published cross-context API**, not an internal entry point. It is one of
only two functions `masterleasing` exposes to another context, and it is the reason
ADR 0003's context split survives contact with term inheritance.

SDD: See `documentation/use-cases/uc09-read-leasing-terms.spec.md`


## 1. Interface

```
masterleasing.core.inport.usecase.ReadLeasingTermsUseCase
```

```kotlin
fun read(query: ReadLeasingTermsQuery): LeasingTermsResult
```

Framework-free. Called by `individualleasing.inbound.driver.IssueIndividualLeasingContractDriver`
and by nothing else — `ContextRegistryTest.individualLeasingReachesMasterLeasingInport_onlyFromADriver`
enforces that only a driver may reach it.


## 2. Method Contract

### 2.1 Input: ReadLeasingTermsQuery

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank, must parse as a UUID |

A `String` rather than `MasterLeasingContractId` because the caller holds the
identity as a plain `String` (ADR 0005 category 2). This driver parses it into the
typed form — the owning context is the one entitled to do that, and requiring the
caller to construct the typed id would mean exporting the type and losing the
independence category 2 exists to protect.

### 2.2 Output: LeasingTermsResult

| Field | Type | Consumer's use |
|---|---|---|
| `masterLeasingContractId` | `String` | correlation |
| `active` | `Boolean` | UC05 I-05 |
| `activationDate` | `LocalDate?` | UC05 I-06; null while `DRAFT` |
| `priceRangeMin` | `Money` | UC05 I-04 |
| `priceRangeMax` | `Money` | UC05 I-04 |
| `employerId` | `EmployerId` | copied onto the lease |
| `lessorId` | `LessorId` | copied onto the lease |
| `configurationVersion` | `Int` | records which version's terms were inherited |

**Every type here is either a Kotlin built-in or a `shared.domain` type.** No
`masterleasing`-owned type crosses: no `MasterLeasingContractId`, no
`MlcConfiguration`, no `MasterLeasingContractStatus`, no `PriceRange`, no
`CreditLimit`.

That constraint is the port's whole value and it is checked structurally by
`ContextRegistryTest.individualLeasing_doesNotImportMasterLeasingInternals` — if an
owned type were added to this result, the consuming context would fail to compile
against its own architecture rules rather than quietly acquiring a dependency.

#### Why `active: Boolean` and not the status enum

The caller asks one question: may I issue a lease under this contract? It does not
need to know that `DRAFT`, `CANCELLED` and `ENDED` are three different ways of
saying no. Exposing `MasterLeasingContractStatus` would put this context's state
model into the other's compile-time dependencies and would mean that adding a state
here is a breaking change there.

#### Why the result is narrower than the configuration

`creditLimit`, `eligibleEmployees`, `returnQuota`, `noticePeriod`, `contractType`,
`salesChannel` and `jointLiability` are all inherited terms and none is returned,
because **nothing consumes them** (UC09 § 3).

A result shaped like the source aggregate rather than like the consumer's need is
the common failure of a published API: every field then has to be maintained,
versioned and reasoned about forever, and narrowing it later is a breaking change.
`architecture.definition.md` § 4.2 asks inport types to be "small and
intention-revealing"; this is what honouring that costs.

Adding a field when a consumer genuinely needs it is cheap. Removing one is not.

### 2.3 Exceptions

| Exception | Condition | Surfaces through UC05 as |
|---|---|---|
| `MasterLeasingContractNotFoundException` | no contract with the given id | `NOT_FOUND` |
| `IllegalArgumentException` | the id string is not a well-formed UUID | `BAD_REQUEST` |

**No exception for a non-active contract.** A `DRAFT` or `CANCELLED` contract
returns successfully with `active = false`. The "you cannot issue under this"
decision belongs to the caller's aggregate (UC05 I-05), not to this query; raising
here would make it indistinguishable from a missing contract.

The exception type crosses the context boundary, which means
`individualleasing` catches — or rather, deliberately does not catch — a
`masterleasing` exception type. That is permitted: `core.domain.<aggregate>.exception`
is reachable from the inport contract by `architecture.definition.md` § 4.2, and the
caller propagates it untouched rather than translating.


## 3. Transaction Boundary

This use case opens **no transaction of its own**. It is called from within UC05's
transaction and participates in it, which gives the caller a consistent read: the
band checked and the contract written are the same snapshot.

It is read-only and MUST stay so. A future implementation that wrote anything here
would be mutating one context's state from inside another's transaction, which no
rule in `architecture.definition.md` § 10 sanctions.


## 4. Implementation

`masterleasing.inbound.driver.ReadLeasingTermsDriver`.

It loads the aggregate through `MasterLeasingContractRepository` rather than reading
a projection, and UC09 § 5 argues why: the answer must respect invariant-checked
state because another context is about to create a contract on the strength of it.
An eventually-consistent projection would let a lease be issued against a price band
amended moments earlier, silently.


## 5. Constraints

- MUST remain framework-free.
- MUST remain read-only: no persistence write, no event publication, no clock.
- `LeasingTermsResult` MUST NOT contain a `masterleasing`-owned type (§ 2.2).
- MUST NOT be called from a controller, a listener or an adapter — only from a
  driver in the calling context (`architecture.definition.md` § 11 rule 3).
- Widening the result is backwards compatible; **narrowing it is a breaking change
  to a published API** and needs the caller migrated first.


## 6. Known Gaps

- **Availability coupling.** If `masterleasing` is unavailable, UC05 cannot issue a
  lease. Accepted in ADR 0003; the alternative — a cached copy of the terms in
  `individualleasing` — fails silently against stale terms rather than loudly
  against an unavailable dependency.
- **No GraphQL surface.** Deliberately (UC09 § 9): exposing it would turn an
  internal cross-context contract into an external one, after which narrowing
  `LeasingTermsResult` becomes an ADR trigger rather than a refactor.
- **One contract per call.** UC05 issues one lease at a time, so there is no batch
  read. A bulk issue path would want one, and adding it is a new function on this
  port rather than a change to this one.
