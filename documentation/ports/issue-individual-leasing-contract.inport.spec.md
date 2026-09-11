# Port Specification – IssueIndividualLeasingContractUseCase (Inport)

## Purpose

The inbound application boundary for UC05 – IssueIndividualLeasingContract: creating
an **Einzel-Leasingvertrag (ELV)** under an existing master contract.

SDD: See `documentation/use-cases/uc05-issue-individual-leasing-contract.spec.md`


## 1. Interface

```
individualleasing.core.inport.usecase.IssueIndividualLeasingContractUseCase
```

```kotlin
fun issue(
    command: IssueIndividualLeasingContractCommand,
): IssueIndividualLeasingContractResult
```


## 2. Method Contract

### 2.1 Input: IssueIndividualLeasingContractCommand

| Field | Type | Constraint |
|---|---|---|
| `masterLeasingContractId` | `String` | non-blank, must parse as a UUID; **opaque** (ADR 0005 category 2) |
| `elvNumber` | `String` | matches `ELV-\d{8}` (I-01) |
| `jobCyclistId` | `String` | non-blank |
| `bikeId` | `String` | non-blank |
| `kauNumber` | `String` | non-blank |
| `configuration` | `IlcConfigurationCommand` | see below |

`IlcConfigurationCommand`:

| Field | Type | Constraint |
|---|---|---|
| `servicePackage` | `ServicePackage` | |
| `inheritanceMode` | `InheritanceMode` | only `COPIED_ONCE` is implemented |
| `currency` | `String` | applies to every amount below |
| `leasingValue` | `BigDecimal` | `>= 0` |
| `leasingFactorPercentage` | `BigDecimal` | `> 0`, `<= 100` |
| `serviceRate` | `BigDecimal` | `>= 0` |
| `insuranceRate` | `BigDecimal` | `>= 0` |
| `residualValue` | `BigDecimal` | `>= 0`, `<= leasingValue` (I-11) |
| `termMonths` | `Int` | `12..60` (I-09) |
| `termStart` | `LocalDate` | not before the master contract's activation date (I-06) |

**Five fields are absent and their absence is contractual:**

- **no `ratePerMonth`** — derived as `leasingValue × leasingFactor`, HALF_UP, scale 4
- **no `termEnd`** — derived as `termStart plus termMonths`
- **no `employerId`, no `lessorId`** — inherited from the master contract, so a lease
  whose employer differs from its contract's employer is unconstructible rather than
  merely invalid
- **no timestamp** — `occurredAt` comes from `ClockPort`. `termStart` is an agreed
  business date and *is* an input (`architecture.definition.md` § 8.1)

Accepting the two derived values would let a caller state a rate that does not follow
from the value and factor it also stated, and all three would be persisted as given
with nothing downstream noticing. They are computed by the driver, asserted by the
aggregate (I-02, I-03), and returned in the result so the caller sees what it got.

### 2.2 Output: IssueIndividualLeasingContractResult

| Field | Type | Description |
|---|---|---|
| `individualLeasingContractId` | `String` | the generated id |
| `elvNumber` | `String` | echoed back |
| `status` | `String` | always `"PENDING_ACTIVATION"` on success |
| `ratePerMonth` | `BigDecimal` | the derived value, scale 4 |
| `termEnd` | `LocalDate` | the derived value |

The two derived values are returned precisely because they were not supplied. A
caller that cannot see what rate it was given has no way to reconcile against its own
records.

`status` is `PENDING_ACTIVATION` even when the master contract is already active.
Activation is UC06's job, and routing both paths through it keeps one activation
rule rather than two.

### 2.3 Exceptions

| Exception | Condition | Classification |
|---|---|---|
| `MasterLeasingContractNotFoundException` | the referenced LRV does not exist (from UC09) | `NOT_FOUND` |
| `MasterContractNotActiveException` | the LRV is not `ACTIVE` (I-05) | `CONFLICT` |
| `PriceOutsideContractBandException` | `leasingValue` outside the LRV's band (I-04) | `CONFLICT` |
| `TermStartBeforeMasterActivationException` | `termStart` before the LRV's activation date (I-06) | `CONFLICT` |
| `InvalidIndividualLeasingContractException` | any own-input invariant (I-01, I-02, I-03, I-09 to I-11) | `BAD_REQUEST` |
| `IllegalArgumentException` | `Money`/`Percentage` invariant, or malformed id | `BAD_REQUEST` |

**The three inherited-term failures classify as `CONFLICT`, not `BAD_REQUEST`**, and
each is a distinct exception type. The request was well-formed and was refused
because of the *state of another aggregate*: a client that gets `CONFLICT` should
look at the master contract, one that gets `BAD_REQUEST` should look at its own
input. Three types rather than one because the three remedies differ — wait for
activation, renegotiate the band, move the start date.

`MasterLeasingContractNotFoundException` is `masterleasing`'s type, propagated
untouched from UC09. This context does not catch and re-wrap it: doing so would add
a type that says the same thing and would hide which context decided.


## 3. Transaction Boundary

Owned by the driver.

The cross-context call to `ReadLeasingTermsUseCase` (UC09) happens **inside** it and
is read-only, which gives a consistent read: the band checked and the lease written
are the same snapshot.

This is not a shared *write* transaction — `individualleasing` writes, `masterleasing`
only reads — so the four conditions in `architecture.definition.md` § 10 do not apply.
It is the second sanctioned form in § 11 rule 3: a synchronous call to another
context's published inport, from the orchestrating driver.


## 4. Implementation

`individualleasing.inbound.driver.IssueIndividualLeasingContractDriver`.

Two ordering properties are part of the contract:

- **Own-input validation precedes the cross-context call.** A malformed `elvNumber`
  is a `BAD_REQUEST` without costing `masterleasing` a database round trip, and never
  depends on that contract existing.
- **`LeasingTermsResult` is mapped into this context's `InheritedLeasingTerms`
  before reaching the aggregate.** That mapping is the anti-corruption step: the
  aggregate depends on a local type, so a change to the other context's result shape
  reaches exactly one function rather than the domain (ADR 0003).

The rules live in the aggregate, not here. The driver fetches the band; `issue`
decides whether the price is allowed. Deleting this driver must not make a lease
issuable outside its band (`domain-vs-use-case.definition.md` § 4).


## 5. Constraints

- The interface MUST remain framework-free.
- The command MUST NOT gain `ratePerMonth`, `termEnd`, `employerId`, `lessorId` or a
  timestamp (§ 2.1).
- `masterLeasingContractId` MUST stay a `String` and MUST NOT be parsed for meaning.
- The driver is the **only** class permitted to call `masterleasing`'s inport —
  `ContextRegistryTest.individualLeasingReachesMasterLeasingInport_onlyFromADriver`.
- `InheritedLeasingTerms` MUST be declared in this context, and the aggregate MUST
  NOT reference `LeasingTermsResult`.


## 6. Known Gaps

- **The credit limit and the eligible-employee headcount are not enforced**
  (UC05 § 8). Both need a count across sibling leases, which is a read-side question,
  and enforcing one here would make a cross-aggregate invariant —
  `modelling.definition.md` calls that a modelling error rather than a
  transaction-scoping one. Left unimplemented rather than approximated, because a
  credit check that is approximately right will be trusted.
- **`elvNumber` uniqueness is enforced only by a database constraint**, so a
  duplicate surfaces as `DuplicateKeyException` and classifies as `INTERNAL_ERROR`
  rather than `CONFLICT`
  (`individual-leasing-contract-repository.outport.spec.md` § 6).
- **Availability coupling on `masterleasing`.** Accepted in ADR 0003; the cached-copy
  alternative fails silently against stale terms.
- **No bulk issue.** One lease per call. A bulk path would want a batch read on
  UC09's port as well.
