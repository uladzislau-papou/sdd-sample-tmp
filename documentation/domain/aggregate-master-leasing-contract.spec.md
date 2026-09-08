# Domain Specification – MasterLeasingContract

## Purpose

The framework agreement with an employer that governs the terms under which that employer's
staff may lease — a *Leasingrahmenvertrag* (LRV). It is the record of authority for its own
status and terms (`adr/0017-contract-data-ownership-boundary.adr.md`), and nothing downstream
in the MVP exists without one: an individual lease is issued *under* an LRV, and every display
and every termination presupposes one.

Bounded context: `mlc` (`architecture.definition.md` § 11,
`adr/0015-two-contexts-by-contract-level.adr.md`).

> **Much of this document rests on provisional decisions, not on sources.** The JCM data model
> page lists every field and its meaning and states **no optionality and no validation rules
> anywhere**. Which fields are mandatory (**PD-01**) and what makes a value valid (**PD-10**)
> were therefore decided by us and are recorded in
> `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 2, each with what
> changes if the project answers differently. Where an invariant below carries a `PD` marker,
> that marker is the provenance — and it is deliberately not restated here, because a rule
> whose reasoning lives in two places is a rule with two places to drift.

SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`.


## 1. Aggregate Root

Name: `MasterLeasingContract`
Package: `mlc.core.domain.masterleasingcontract`

It is the consistency boundary over itself and its current terms version. `ContractConfiguration`
is an **entity inside** the aggregate, not an aggregate of its own: it has an identity, but it
is never loaded or changed independently of the contract that points at it. The source's
`mlc_config_id` is defined as "the current configuration version", which is what makes the
contract the boundary and the configuration the part.

### Identity

| Type | Category (`adr/0005`) | Note |
|---|---|---|
| `MasterLeasingContractId` | 1 — own identity | UUID, Value Object, never in `shared` |
| `ContractConfigurationId` | 1 — own identity | The source's `mlc_config_id` |
| `EmployerId` | 3 — owned by no context here | **Local to `mlc`, not shared** — `adr/0023` |
| `LessorId` | 3 | Local to `mlc` — `adr/0023` |
| `PartnerNumber` | 3 | Local to `mlc` — `adr/0023` |

The last three are the interesting row. `adr/0005` category 3 *permits* a shared Value Object
and its Consequences reserve the choice to an ADR;
`adr/0023-participant-identities-are-context-local.adr.md` decided **against** promotion,
because category 3's test requires a second context referencing them and `ilc` does not exist
yet. Promoting them when it does is a new ADR.


## 2. Invariants (Always-Valid)

Each is enforced at construction, and each throws
`InvalidMasterLeasingContractException` — a domain exception rather than
`IllegalArgumentException`, because every one of these values arrives from a command, so the
value object is the last guard guaranteed to run (`coding-style.definition.md` § 6.2 clause
one, and `adr/0023` § Consequences).

**Sourced — the only one:**

- A contract cannot exist without an employer. `employerId` is non-blank. **Derived** from the
  MVP page and `project.definition.md`: this contract *is* the agreement established with an
  employer, so an instance without one is not the thing being modelled.

**Presence, per PD-01:**

- `lessorId` non-blank
- `contractType` non-blank
- `partnerNumber` non-blank **when present** — absence is a null, not a blank string

**Plausibility, per PD-10:**

- `creditLimit` > 0
- `currency` matches `[A-Z]{3}` — a *shape*, not membership of a currency table
- `eligibleEmployees` > 0
- `returnQuotaPercentage` and `earlyClaimFeePercentage` in `0..100` inclusive, when present
- `earlyClaimWindowMonths` >= 0 when present
- `servicePackageVersion` >= 1 when present
- `priceRangeMin` and `priceRangeMax` are non-negative, `priceRangeMin <= priceRangeMax`, and
  the two are supplied **together or not at all**

> **PD-10's rules are of one narrow kind, and the narrowness is the design.** They reject only
> values that could not be meaningful under any validation matrix — a negative price, a
> percentage above 100, a minimum above a maximum, a currency that is not a currency code.
> None encodes a threshold or a limit the business might set differently. The MVP page's
> **risk 1** records that the real linkage rules are not modelled anywhere and that the
> measure against it is to *build the matrix*; the matrix does not exist. The danger is not
> that a rule here is too strict — that surfaces as a rejected creation and gets fixed — but
> that these come to look like *the* matrix once PD-10's marking is lost, and the real one then
> never gets built.

**Structural:**

- A contract points at exactly **one** current configuration, and it is version `1` at
  creation.
- The terms are immutable. Superseding them means a new `ContractConfiguration` at a higher
  version, through a method on the aggregate — not an assignment from a driver.

**Not an invariant, deliberately:**

- One active contract per employer. **PD-05** permits several. It is recorded here because its
  absence is a decision: `project.definition.md`'s last-write-wins non-goal means the rule
  could not be enforced in the aggregate at all — two concurrent creations would each read "no
  existing contract" and both succeed — so it would need a database constraint.
- The employer's or lessor's existence. **PD-06** trusts the caller. Verification would add the
  first outbound port reaching a foreign system, and *which* system is itself unsourced.


## 3. State Model

Possible states (`MasterLeasingContractStatus`):

- `ACTIVE`
- `TERMINATED`

Allowed transitions:

- *(none)* → `ACTIVE` — creation (UC07)
- `ACTIVE` → `TERMINATED` — **not implemented**; the use case is blocked on the status list

Illegal transitions:

- `TERMINATED` → `ACTIVE`. A terminated framework agreement is not revived; a new agreement is
  created.

> **The set is expected to grow, and `TERMINATED` is unreachable today.** **PD-07** takes both
> values from the MVP page's *"Aktiv → Beendet"*, and the same line carries the unresolved
> *"ToDo: welche Stati brauchen wir hier noch?"*. `TERMINATED` is declared anyway because the
> source states the lifecycle has two ends, and a single-valued status enum invites the next
> author to ask why the field exists.
>
> An enum and a method, not a state-machine framework, per
> `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`. That ADR's argument applies
> with full force here: an unsettled lifecycle argues *against* declarative configuration,
> because changing an enum and a method with a failing test in front of it is a compile-time
> exercise while changing a state machine's configuration is a runtime one, whose failure mode
> is a transition that silently no longer fires.


## 4. Behavior

### `MasterLeasingContract.create(id, employerId, lessorId, partnerNumber, owner, configuration, now)`

- **Preconditions:** every value object already constructed, which is where §2's rules ran.
- **Postconditions:** status `ACTIVE`; `creationTime` and `activationDate` both equal `now`;
  `currentConfiguration` is the supplied configuration at version 1.
- **Emits:** `MasterLeasingContractCreated`.

The factory itself validates **nothing**, and that is deliberate. Every §2 rule lives in the
value object or in `ContractConfiguration.create` that owns it, so each is reachable from a
domain test — and a rule duplicated in the factory would be a rule with two places to drift,
which matters because PD-10 is expected to change.

`now` is supplied by the driver from `ClockPort` and never read here:
`architecture.definition.md` § 8 forbids the domain from calling `Instant.now()`, and § 8.1
forbids an external transport from supplying one (**PD-11**).

**On `creationTime` and `activationDate` being equal.** They are separate fields because the
source defines them as separate facts, and **PD-07** is what currently makes them coincide — a
contract that is active on creation activates when it is created. A pre-active status would
separate them, which is the change PD-07 names.

### `MasterLeasingContract.reconstitute(...)`

Rebuilds from persisted state with no pending events, re-checking **no** invariant.

That is not laziness. The value objects it rebuilds enforce PD-10, a provisional matrix
expected to be replaced. Re-validating on read would mean tightening a rule makes previously
written contracts unloadable — and the terms of a signed agreement are exactly the thing that
must not become unreadable because we changed our mind. `ClassRoleRulesTest` restricts the
caller to `..outbound.persistence..`, matching any `reconstitute` in `core.domain`.

> **This claim was made before it was true, and the correction is the mechanism.** The first
> implementation called `MasterLeasingContract.reconstitute` and `ContractConfiguration.reconstitute`
> throughout — which re-check nothing — but those factories receive *already-constructed* value
> objects, and the mapper built them through **validating** constructors. So the read path
> validated everything while this section said it did not. `ddd-hex-reviewer` caught it; nothing
> else could have, because `MasterLeasingContractJpaRepositoryIT` only round-trips values that
> satisfy the current rules, leaving the claim unfalsifiable in both directions.
>
> Fixed structurally rather than by softening the claim. **Every value object above has a
> private primary constructor**; validation lives in a companion `invoke`, and each carries a
> `reconstitute` the read path uses instead. `@ConsistentCopyVisibility` makes the generated
> `copy()` private too — which closed a hole the original public-constructor version had, since
> `copy(value = …)` was a back door around validation that nothing had noticed.
>
> `ReconstitutionTest` makes the property **falsifiable**: every case stores a value that
> violates a current rule and asserts the read path accepts it, and
> `ReconstitutionTest.invoke_stillValidates_soReconstituteIsTheOnlyWayPast` asserts the other
> half — that moving validation into `invoke` did not quietly disarm the write path.
>
> One further defect surfaced during the fix and is worth recording, because the shape recurs.
> Inside a companion object, `PriceRange(min, max)` resolves to the **private constructor**, not
> to `invoke` — so `PriceRange.of` silently stopped validating for one compile. It was caught by
> `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenPriceRangeMinExceedsMax`,
> which is the argument for AC-07 existing at the driver as well as at the value object.

### `pullDomainEvents()`

Returns and clears the recorded events; a second call returns an empty list. The snapshot is a
copy, so a caller iterating it cannot be surprised by a later transition.

### `ContractConfiguration.create(...)` / `.reconstitute(...)`

`create` enforces the two §2 rules that guard a plain `Int` — `earlyClaimWindowMonths` and
`servicePackageVersion` — because a one-field value object per bounded `Int` would add two
types carrying no meaning beyond their range. `servicePackageOptions` is copied at
construction, so a caller keeping its own mutable list cannot change the terms afterwards.


## 5. Domain Events

| Event | Payload | Raised by |
|---|---|---|
| `MasterLeasingContractCreated` | `masterLeasingContractId`, `occurredAt` | `create` |

**Coined, not sourced.** No source names any event or says what one carries. `adr/0019`
requires a spec to name the events it emits and `modelling.definition.md` fixes past tense, so
the name is invented in `uc07` § 5 step 7 rather than adopted — and because it becomes the
outbox record's type, it is a **consumer-visible contract**: renaming it later breaks whoever
reads the outbox.

The payload is derived and deliberately minimal — the identifier, because the dispatcher needs
to find the contract, and the moment, because a fact's own timestamp is part of the fact.
Nothing in the MVP requires more, and every field added here is a field a consumer may come to
depend on.


## 6. Tests

| Subject | Test |
|---|---|
| Aggregate creation, events, reconstitution | `MasterLeasingContractTest` |
| Terms entity, the two `Int` rules, list copying | `ContractConfigurationTest` |
| Each value object's invariant | `EmployerIdTest`, `LessorIdTest`, `PartnerNumberTest`, `ContractTypeTest`, `CreditLimitTest`, `CurrencyCodeTest`, `EligibleEmployeesTest`, `PercentageTest`, `PriceRangeTest` |
| Orchestration and the `AC-NN` criteria | `CreateMasterLeasingContractDriverTest` |
| Persistence roundtrip | `MasterLeasingContractJpaRepositoryIT` |
| The read path accepting what the write path rejects | `ReconstitutionTest` |
| GraphQL error classification, including the unreachable ones | `MlcGraphQLExceptionResolverTest` |

`SpecCitationsTest` checks the `Class.method` citations in `uc07` § 10 against the code. Note
its limit, which applies to the middle row above: **its regex matches `Class.method` only**, so
a citation naming a class alone — as that row does — is invisible to it. The per-method
citations are in `uc07` § 10.
