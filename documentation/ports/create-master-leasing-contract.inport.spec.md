# Port Specification – CreateMasterLeasingContractUseCase (Inport)

## Purpose

Defines the inbound application boundary for UC07 – CreateMasterLeasingContract. This inport is
the only entry point for bringing a master leasing contract into existence from outside the
core.

**Described from the domain's needs, not from a caller's message.** The eventual trigger is
employer onboarding (AGO), whose message format is documented nowhere and is deliberately not
guessed at (`project.definition.md` non-goals). A GraphQL mutation is this port's *first*
adapter (`adr/0020-graphql-as-the-only-transport.adr.md`), not its definition — which is the
whole point of having a port here rather than a controller calling a service.

SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md`


## 1. Interface

```
mlc.core.inport.usecase.CreateMasterLeasingContractUseCase
```

## 2. Method Contract

```
CreateMasterLeasingContractResult create(CreateMasterLeasingContractCommand command)
```

### 2.1 Input: `CreateMasterLeasingContractCommand`

Standard-library types only — no domain value objects. Structural validation happens when the
driver maps the command onto domain types, so the domain remains the guard even for a caller
with no adapter in front of it (`coding-style.definition.md` § 6.2). That is not hypothetical:
AGO will not be a GraphQL client.

> **Which fields are required is `PD-01` — our decision, not a sourced rule.** The JCM data
> model page lists every field and states no optionality anywhere. PD-01 errs toward *fewer*
> mandatory fields on purpose, because making an optional field mandatory later breaks existing
> rows while the reverse does not.

**Required**

| Field | Type | Constraint | Enforced by |
|---|---|---|---|
| `employerId` | String | non-blank | `EmployerId` |
| `lessorId` | String | non-blank | `LessorId` |
| `contractType` | String | non-blank | `ContractType` |
| `creditLimit` | BigDecimal | > 0 | `CreditLimit` |
| `currency` | String | `[A-Z]{3}` | `CurrencyCode` |
| `eligibleEmployees` | Int | > 0 | `EligibleEmployees` |

**Optional** — for each, what absence *means* is documented on the field, as
`coding-style.definition.md` § 1.4 requires.

| Field | Type | Constraint when present | Absence means |
|---|---|---|---|
| `partnerNumber` | String? | non-blank | the caller does not yet hold the Odoo/Radar join key (**PD-03**) |
| `owner` | String? | — | no team assigned (**PD-09**: a team, not the acting user) |
| `salesChannel` | String? | — | not recorded |
| `groupJointLiability` | Boolean | — | *not nullable*; the adapter defaults it to `false` |
| `returnQuotaPercentage` | BigDecimal? | `0..100` | no return quota agreed |
| `earlyClaimFeePercentage` | BigDecimal? | `0..100` | no early-claim fee agreed |
| `earlyClaimWindowMonths` | Int? | >= 0 | no early-claim window agreed |
| `noticePeriodRule` | String? | — | no explicit notice rule |
| `paymentTerms` | String? | — | no payment-terms code agreed |
| `priceRangeMin` | BigDecimal? | >= 0, <= `priceRangeMax` | no price band agreed |
| `priceRangeMax` | BigDecimal? | >= 0 | no price band agreed |
| `calculationBasis` | String? | — | not recorded |
| `servicePackageOptions` | List\<String\> | — | *not nullable*; empty means no tiers offered |
| `servicePackageVersion` | Int? | >= 1 | service-package terms unversioned |
| `categoriesEditableInPortal` | Boolean | — | *not nullable*; the adapter defaults it to `false` |

**Two properties of this table are the contract, not incidental.**

**There is no timestamp field.** `activationDate` and `creationTime` are both the moment of
creation, read from `ClockPort` by the driver (**PD-08**), and `architecture.definition.md`
§ 8.1 forbids an external transport from supplying one at all (**PD-11**).
`TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes` enforces the adapter half.

**The two price bounds must be supplied together or not at all.** They are two nullable fields
here and one nullable `PriceRange` in the domain, so the mapping has four combinations. The
rule lives in the domain factory `PriceRange.of` and **not** in the driver, because
`architecture.definition.md` § 4.4 leaves a driver no rules of its own. This rule was found
during implementation rather than specified — see `uc07` § 2.3.

### 2.2 Output: `CreateMasterLeasingContractResult`

| Field | Type | Note |
|---|---|---|
| `masterLeasingContractId` | String | UUID as text |
| `status` | String | `ACTIVE` on creation (**PD-07**) |
| `configurationId` | String | the source's `mlc_config_id` |
| `configurationVersion` | Int | always `1` on creation |

**Derived, not sourced.** No source specifies a response shape. `uc07` § 3 derives these four
from what the caller cannot otherwise learn: this service has no read side, so a caller not
told the identifiers has no second way to obtain them. Because a domain test cannot falsify the
derivation, `api/uc07-create-master-leasing-contract.graphql` is what holds the shape
accountable.

### 2.3 Errors

| Exception | Condition | GraphQL |
|---|---|---|
| `InvalidMasterLeasingContractException` | any § 2.3 rule of `uc07` violated | `BAD_REQUEST` |
| `IllegalArgumentException` | malformed decimal on the wire — adapter-level, never reaches this port | `BAD_REQUEST` |
| *(infrastructure)* | persistence failed | `INTERNAL_ERROR` |

**One domain exception covers every rule**, deliberately. That is what lets the classification
list stay complete while PD-01 and PD-10 are provisional: when a rule changes, only the
*condition* widens and no consumer-visible error moves.

`NOT_FOUND` is unreachable: this operation creates rather than loads, verifies no participant
(**PD-06**) and resolves no parent (**PD-04**).

## 3. Transaction

The **driver** owns the boundary (`@Transactional` on `CreateMasterLeasingContractDriver`). A
caller MUST NOT wrap this call in its own transaction: `adr/0022` has the outbox row written
inside this method's transaction, so an adapter owning the boundary would own a domain
guarantee.

## 4. Tests

`CreateMasterLeasingContractDriverTest` — one case per `AC-NN` in `uc07` § 7, cited by method
name in that spec's § 10.
