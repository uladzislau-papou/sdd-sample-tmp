# Use Case Specification – CreateMasterLeasingContract

## Status
SPECIFIED

> **It was `BLOCKED`, and how it stopped being blocked is the most important thing on this
> page.** Eleven open questions — OQ 1 through OQ 11 — reached § 2, § 3, § 4, § 5, § 7, § 8
> and § 9, and none of them has been answered by the JCM project. They were closed by
> **our own decision**, recorded below as **PD‑01 … PD‑11**, so that the implementation
> could proceed and the SDD machinery could be exercised end to end.
>
> **A provisional decision is not a source, and the distinction is load-bearing.** Every PD
> block states what was decided, that it is unsourced, and *what changes if the project
> answers differently*. They are marked so that `spec-reviewer` — whose job is hunting
> claims with no source — reports them as decisions rather than as invention, and so that a
> reader can tell in one glance which parts of this spec rest on a Confluence page and which
> rest on us. Anything not inside a PD block and not marked `[author's reading]` is sourced.
>
> **This is the failure mode this repository was built around**, stated in `HANDOFF.md` § 5
> as *a summary is not a source*. A provisional decision that loses its marking becomes a
> sourced requirement within one edit. The `PD‑NN` identifiers exist to make that edit
> visible.
>
> OQ 12 was closed differently — by
> `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md`, which is a real
> decision with real reasoning, not a placeholder. It is not a PD.
>
> The status vocabulary now carries `BLOCKED`, and `/loop-uc`'s preflight refuses on it — see
> `use-case.spec.template.md`. That was OQ 11's neighbour on the to-do list: this spec's
> blocked state used to be legible only in prose, in the one document the loop re-reads every
> iteration.

## Bounded Context
`mlc` — triggered via GraphQL (`adr/0020-graphql-as-the-only-transport.adr.md`), invoked by a
contract administrator in the Backoffice UI (PD‑09).

The consumer is sourced: `project.definition.md` states that the near-term product is a
backend for the internal Backoffice UI used by contract administrators, and `adr/0020`
records that this UI speaks GraphQL. That an administrator *creates* a master contract is
**not** sourced — the MVP page says the trigger is mocked and will eventually be employer
onboarding, and the component view assigns the forwarding of onboarding data to the
onboarding service. PD‑09 decides it.

The context is decided by `adr/0015-two-contexts-by-contract-level.adr.md` and is registered
in `architecture.definition.md` § 11. The registry is parsed in both directions, so the row
and the package land in the same increment; § 10 carries the box.

## Purpose
Bring a master leasing contract (*Leasingrahmenvertrag*, LRV) into existence together with
the first version of its terms.


## 1. Intent

**Source:** Confluence `JCM` → **MVP** (page 573112322, modified 2026‑07‑22), scope item
*"Anlage eines LRVs (Trigger wird gemockt, eigentlich: AGO)"* and *"Status-Lifecycle: Aktiv
→ Beendet (ToDo: welche Stati brauchen wir hier noch?)"*. Field-level detail from **Contract
Management Domain Data model** (page 740065284, modified 2026‑09‑01), entities
`MASTER_LEASING_CONTRACT` and `MLC_CONFIGURATION`.

A master leasing contract is the framework agreement with an employer that governs the terms
under which that employer's staff may lease (`project.definition.md`). Nothing downstream in
the MVP exists without it: an individual lease is issued *under* an LRV, and every display
and every termination presupposes one.

The business outcome is that a contract exists in this service as the record of authority for
its own status and terms — the position
`adr/0017-contract-data-ownership-boundary.adr.md` takes.

**Trigger.** The eventual trigger is employer onboarding (AGO); the MVP mocks it. Per
`project.definition.md`'s non-goals the onboarding service's message format is documented
nowhere and is deliberately not guessed at, so the inbound port is described from the
domain's needs and a GraphQL mutation is its first adapter (`adr/0020`).

> **Sources that could not be read.** The MVP page's attachments are outside the granted
> Confluence scopes. The data model page links an external **Miro board** carrying the model
> diagram; relationships, cardinalities and optionality are the natural content of such a
> board, and optionality is what OQ 1 asked for.
>
> **The board has still not been read** — it is outside the agent's Confluence scopes and
> needs a human. It is therefore *not* the basis for any decision below: PD‑01 and PD‑10
> were taken without it, which is precisely why they are provisional. Reading it remains the
> cheapest way to replace PD‑01, PD‑02, PD‑04 and PD‑10 with sourced statements, and § 10
> keeps a box for it.


## 2. Input Contract

> **§ 2's open questions are closed by PD‑01, PD‑02, PD‑03, PD‑04, PD‑06, PD‑08, PD‑09,
> PD‑10 and PD‑11.** The field *list* below is sourced. Which fields are **mandatory**, and
> what makes a value **valid**, are ours — and under the Always-Valid doctrine
> (`modelling.definition.md`) that is exactly what the aggregate's constructor needed to
> know, which is why nothing could be built until they were decided.

**On the wording of the field descriptions.** The source is German and the descriptions below
are English. `project.definition.md` states that specifications are written in English
against German sources and that the translation is therefore part of the spec — so a
description in quotation marks is a **translation of the source's own entry**, not a
quotation of it, and a disputed term is settled at the page rather than here. Where the
source's entry is a bare field label with no description, the gloss is marked **[author's
reading]** and is not sourced at all.

**Identifying the parties.** All three are **external** identities:
`adr/0017-contract-data-ownership-boundary.adr.md` puts the employer, the lessor and the
partner number in the "not ours" column, and they arrive through the anti-corruption layer.

Per `adr/0005-bounded-context-identity-boundaries.adr.md` these are **category 3** —
identities owned by no context in this system, which *may* be a shared Value Object. They are
**not** category 2 (a reference to another of *our* contexts, carried as an opaque `String`).
The distinction is load-bearing and easy to get wrong: `adr/0017` § "Decision" states that a
participant is "held as an opaque identity value, per `adr/0005`", which reads as category 2
while describing category 3.

**And the choice is now decided.**
`adr/0023-participant-identities-are-context-local.adr.md` makes `EmployerId`, `LessorId` and
`PartnerNumber` Value Objects **local to `mlc`**, not members of `shared.domain`, because
ADR‑0005's category‑3 test requires a second context referencing them and `ilc` does not
exist yet. That ADR was required by `adr/0005`'s Consequences — a reservation distinct from
the canonical trigger list in `sdd.playbook.md` § 6, which `CLAUDE.md` makes the only such
list.

### 2.1 The contract itself (`MASTER_LEASING_CONTRACT`)

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `employerId` | `String` | **yes** | "Reference to the Employer (external)" [*Arbeitgeber*] |
| `lessorId` | `String` | **yes** | "Reference to the Lessor (external)" [*Leasinggeber*] |
| `partnerNumber` | `String?` | no | "Join key between Odoo and Radar" [*Partnernummer*]. Absent when the caller does not yet hold it (PD‑03) |
| `owner` | `String?` | no | "Internal user/team who owns this record". A **team** assignment supplied by the caller (PD‑09). Absent when no team has been assigned |

### 2.2 The first version of the terms (`MLC_CONFIGURATION`)

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `creditLimit` | `BigDecimal` | **yes** | "The maximum credit exposure allowed on this contract" |
| `contractType` | `String` | **yes** | "The commercial model the contract runs under (e.g. salary-sacrifice leasing)" |
| `currency` | `String` | **yes** | [*Währung*]. The source gives the German term and nothing else; PD‑10 fixes the format |
| `eligibleEmployees` | `Int` | **yes** | "The number of employees entitled to lease under this contract" [*JobRad-Berechtigte*] |
| `salesChannel` | `String?` | no | No description in the source. **[author's reading]** the channel or segment through which the contract was sold |
| `groupJointLiability` | `Boolean` | no, defaults `false` | "Whether this contract shares joint liability with other companies in its corporate group" [*gesamtschuldnerische Haftung*]. **Renamed** from the source's `kuv_joint_liability` — see PD‑00 |
| `returnQuotaPercentage` | `BigDecimal?` | no | "The share of active leases per year that can be dissolved without penalty" [*Rückgabekontingent*] |
| `earlyClaimFeePercentage` | `BigDecimal?` | no | "The fee owed if the return quota is claimed within the early-claim window" |
| `earlyClaimWindowMonths` | `Int?` | no | "The number of months within which claiming the quota counts as 'early'" |
| `noticePeriodRule` | `String?` | no | "How much notice either party must give to terminate, and under which terms version" [*Kündigungsfrist*] |
| `paymentTerms` | `String?` | no | "A code identifying the agreed payment terms" |
| `priceRangeMin` | `BigDecimal?` | no | "The lowest bike price this contract's conditions permit leasing" |
| `priceRangeMax` | `BigDecimal?` | no | No description in the source. **[author's reading]** the highest such price |
| `calculationBasis` | `String?` | no | "How the leasing price is derived from the bike's sale price and shipping cost" |
| `servicePackageOptions` | `List<String>` | no, defaults empty | "The list of service tiers employees can choose from under this contract" [*Servicepakete*] |
| `servicePackageVersion` | `Int?` | no | "The version number of the service-package terms, tracked separately from the contract version" |
| `categoriesEditableInPortal` | `Boolean` | no, defaults `false` | "Whether the employer can edit bike categories themselves in the manager portal" |

### 2.3 Validation rules

One rule is a **labelled derivation** and stands apart from the PDs, because it is argued
from the sources rather than chosen:

- `employerId` is required. **Derived**: the MVP page and `project.definition.md` define this
  contract as the framework agreement established *with an employer*, so an instance without
  one is not the thing being modelled. AC‑03 and the first row of § 3's error table rest on
  this derivation, so if it is wrong, both change.

Everything else in the table below is **PD‑01** (presence) and **PD‑10** (plausibility).

| Rule | Enforced by | Origin |
|------|-------------|--------|
| `employerId` non-blank | `EmployerId` | derivation above |
| `lessorId` non-blank | `LessorId` | PD‑01 |
| `partnerNumber` non-blank **when present** | `PartnerNumber` | PD‑01 |
| `contractType` non-blank | `ContractType` | PD‑01 |
| `creditLimit` > 0 | `CreditLimit` | PD‑10 |
| `currency` matches `[A-Z]{3}` | `CurrencyCode` | PD‑10 |
| `eligibleEmployees` > 0 | `EligibleEmployees` | PD‑10 |
| `returnQuotaPercentage` in `0..100` when present | `Percentage` | PD‑10 |
| `earlyClaimFeePercentage` in `0..100` when present | `Percentage` | PD‑10 |
| `earlyClaimWindowMonths` >= 0 when present | `ContractConfiguration.create` | PD‑10 |
| `servicePackageVersion` >= 1 when present | `ContractConfiguration.create` | PD‑10 |
| `priceRangeMin` >= 0, `priceRangeMax` >= 0, and `priceRangeMin` <= `priceRangeMax` — all three only when **both** are present | `PriceRange` | PD‑10 |
| `priceRangeMin` and `priceRangeMax` are supplied **together or not at all** | `PriceRange.of` | PD‑10 |

The last row was **not in the blocked revision, and it is not a widening of PD‑10 — it is a
gap the implementation exposed.** The command carries two nullable bounds while the domain
carries one nullable range, so the mapping has four input combinations and § 2.3 named a rule
for only two of them. A half-specified range describes no band of prices, so it is rejected.

It is enforced by a domain factory, `PriceRange.of(min, max)`, and deliberately **not** by
the driver: `architecture.definition.md` § 4.4 makes a driver an orchestrator with no domain
rule of its own, and "one bound without the other is invalid" is a domain rule. The driver
calls the factory, which is a delegation.

Each rule throws `InvalidMasterLeasingContractException`, a domain exception, because every
one of these values arrives from a command — `coding-style.definition.md` § 6.2 clause one,
and `adr/0023` § Consequences.

**Not in the input contract, with a source:** `dynamicFields`. The source describes it as "a
named extensibility slot for future fields; **no concrete fields were ever defined**". A slot
with no defined contents is not implementable.

**Not in the input contract, by decision:** `serviceAgreementId` (PD‑02), `parentMlcId` and
`mlcParentConfigId`/`inheritanceMode` (PD‑04), `activationDate` and `creationTime` (PD‑08,
PD‑11).

**Established by the use case, not supplied:** `id`, `mlcConfigId`, the configuration's
`version` (always `1` at creation), `status` (PD‑07), `creationTime` and `activationDate`
(PD‑08).

---

### Provisional decisions

Each block states the decision, that it is **not sourced**, and what changes if the project
answers differently. The "if wrong" clause is the point of the format: it is the impact
assessment, written while the reasoning is fresh rather than reconstructed later.

**PD‑00 — `kuv_joint_liability` is named `groupJointLiability` in code.**
`coding-style.definition.md` § 4.3 requires every identifier to be English and forbids
carrying the German term "not as a name and not as an alias". `KUV` is a German
abbreviation, and the source's own description of the field is "joint liability with other
companies in its corporate group". The column keeps the source's name
(`kuv_joint_liability`) so the data model stays recognisable to whoever wrote it; the domain
type does not.
*If wrong:* the mapper's one line and the GraphQL field name. § 4.3 states that a disputed
translation is settled at the source page, not here.

**PD‑01 — mandatory at creation: `employerId`, `lessorId`, `contractType`, `creditLimit`,
`currency`, `eligibleEmployees`. Everything else is optional.**
This was OQ 1, the primary blocker. The six chosen are the ones without which the terms do
not describe a commercial agreement: who the parties are, under what model, up to what
exposure, in what currency, for how many people. The rest can plausibly be filled in later
by an administrator.
*If wrong:* the aggregate's factory signature, `CreateMasterLeasingContractCommand`, the
GraphQL input type's `!` markers, AC‑08, and one test per field that changes side. This is
the widest-reaching PD on the page.
*Note the asymmetry that makes it safer than it looks:* making an optional field mandatory
later breaks existing rows; making a mandatory field optional does not. PD‑01 therefore errs
toward **fewer** mandatory fields than a cautious reading might, and the six are the ones
where a null would make the record meaningless rather than merely incomplete.

**PD‑02 — a service agreement (DLV) is not required at creation and is not an input.**
The source places `service_agreement_id` on `MLC_CONFIGURATION`, and the MVP page lists
*"Verknüpfung mit dem dazugehörigen DLV"* among the **display** items — which does not settle
when the link is made. Decided: attached afterwards, by a use case that does not exist yet.
*If wrong:* one optional field on the command, the input type and the configuration, plus a
required-field rule if it turns out to be mandatory.

**PD‑03 — `partnerNumber` is an optional input supplied by the caller; it is not fetched.**
The source calls it the join key between Odoo and Radar, so it originates in one of them.
Fetching it would add an outbound port to a foreign system and entangle this use case with
the MVP page's **risk 2** (*"Synchronisationsstrategie vor MVP-Start festlegen"*), which is
unresolved. Decided: accept it when the caller has it, store it, do not go looking.
*If wrong:* an outbound port, an adapter, a failure scenario in § 8, and an
`INTERNAL_ERROR`-classified error in § 9 — the first outbound call this use case makes.

**PD‑04 — affiliated and supplementary contracts are out of scope; `parentMlcId`,
`mlcParentConfigId` and `inheritanceMode` are not inputs.**
The source describes `parentMlcId` as pointing "at the base MLC when this one is
affiliated/supplementary". The MVP page excludes *"alle anderen Unternehmensänderungen"* but
never mentions group structures either way, so scoping them out is a decision and not a
reading.
*If wrong:* a self-reference on the aggregate, an inheritance rule over the configuration,
and a `NOT_FOUND` classification in § 9 for a parent that does not exist — the first case in
which this operation loads anything.

**PD‑06 — the employer's existence is not verified. It is trusted from the caller.**
This was OQ 6. Verification would add the first outbound port reaching a foreign system, and
*which* system is itself unsourced: the component view routes partner data through the
partner service to **RADar** while describing **Odoo** as the system of record for
JobCyclist data. Naming either would be a guess, so § 4 keeps the assumption explicit
instead.
*If wrong:* an outbound port in § 6, a `NOT_FOUND` or `BAD_REQUEST` classification, a failure
scenario, and a use-case test with a stubbed verifier.

**PD‑07 — the initial status is `ACTIVE`, and the status set is `ACTIVE` and `TERMINATED`.**
The MVP page states the lifecycle as *"Aktiv → Beendet"*, which reads as "created active" —
so this PD is closer to the source than the others. What makes it provisional is the same
line's unresolved *"ToDo: welche Stati brauchen wir hier noch?"*: the set is open, and a
`DRAFT` or `PENDING` status arriving later would change what creation produces.
*If wrong:* `MasterLeasingContractStatus`, the aggregate's factory, AC‑05, and — if a
pre-active status appears — PD‑08, because a contract that is not yet active has no
activation date.

**PD‑08 — `activationDate` is not an input. It and `creationTime` are both the moment of
creation, read from `ClockPort`.**
This was OQ 8. The source defines `activationDate` as "when the MLC became active" without
saying who sets it. Two facts decide it together: PD‑07 makes the contract active at
creation, so the activation moment *is* the creation moment; and PD‑11 forbids a timestamp
on the GraphQL input, which closes the caller-supplied branch by our own architecture rather
than by the business.
*If wrong:* if a contract may be agreed on one date and activated on another, `activationDate`
becomes an input — and `architecture.definition.md` § 8.1 then has to say how, because a
client asserting it is exactly what that section forbids. That is an amendment to a document
ranking above this spec, not a change to this one.

**PD‑09 — a contract administrator invokes this use case through the Backoffice UI, and
`owner` is a team assignment supplied with the data.**
This was OQ 9, which had two sub-questions and one answer between them. The Backoffice UI is
the sourced consumer; that an administrator *creates* is not. The MVP page says the trigger
is mocked and will eventually be AGO, and the component view has the onboarding service
forwarding onboarding data "to support master-agreement creation".
Reading `owner` as a **team** rather than as the acting operator is what makes this
buildable: this service has no authentication —
`adr/0021-operational-baseline-from-the-platform.adr.md` adopts the operational baseline and
explicitly stops short of it — so an `owner` meaning "the user who did this" could not be
populated at all today.
*If wrong:* if `owner` identifies the acting operator, it cannot be an input; it must come
from an authenticated principal, and this use case gains a dependency on the authentication
increment that `HANDOFF.md` § 7 lists as outstanding.

**PD‑10 — the field-level validity rules are the ones tabulated in § 2.3.**
This was OQ 10, and it is the PD with the weakest footing, which is worth saying plainly.
The MVP page's **risk 1** records that the linkage rules — limits, entitlements, status
dependencies — *"sind im Moment nicht in Navax abgebildet"*, and the measure recorded against
that risk is to **build the matrix**. The matrix does not exist. So every rule in § 2.3 is a
plausibility check we invented, not a business rule we found.
They are deliberately of one narrow kind: rules that reject values that could not be
meaningful under any matrix — a negative price, a percentage above 100, a minimum above a
maximum, a currency that is not a currency code. None encodes a threshold, a limit or a
relationship between fields the business might set differently.
*If wrong:* each rule is one value object and one domain test. The risk is not that a rule is
too strict — that surfaces as a rejected creation and gets fixed. The risk is that these
look like *the* validation matrix once PD‑10's marking is lost, and the real matrix then
never gets built. That is why AC‑07 names the rule it asserts rather than asserting "input is
validated".

**PD‑11 — closed, and not by us.** OQ 11 asked whether a GraphQL client may supply a
timestamp. `architecture.definition.md` § 8.1 now carries an `inbound.graphql` row: `ClockPort`
only, no timestamp field on the input type, with
`TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes` as its executable owner.
That is an edit to a document ranking above this spec, which is where the question belonged —
it was a gap in our own definition, not in the sources. Kept in this list as a pointer so the
numbering does not have a hole.


## 3. Output Contract

Return type — success payload. **Derived, not sourced:** no source specifies a response
shape. What it must contain follows from what the caller cannot otherwise learn:

- the created contract's identifier
- its status
- the identifier and version of the configuration created with it

Because the derivation is unfalsifiable by a domain test — AC‑02 observes through the
repository, and this service has no read side — the payload's shape is closed by the
executable request in `api/uc07-create-master-leasing-contract.graphql` (§ 9), and § 10 holds
that file accountable for it. If the derivation is wrong, that file is where it shows.

Error types:

| Exception | Condition | GraphQL classification |
|-----------|-----------|------------------------|
| `InvalidMasterLeasingContractException` | Any rule in § 2.3 is violated | `BAD_REQUEST` |
| — (infrastructure failure, not a domain exception) | Persistence failed | `INTERNAL_ERROR` |

No HTTP column: there is no REST transport (§ 9).

The list is now **complete for the rules § 2.3 states**, which is a narrower claim than
"complete". PD‑01 and PD‑10 decide what those rules are; if either changes, this table's
first row still holds — one domain exception covers every § 2.3 rule — and only its
*Condition* cell widens.

**PD‑05 — one employer may hold more than one active master contract. There is no uniqueness
rule and no conflict error.**
This was OQ 5. No source addresses it, and the existence of `parentMlcId` for affiliated
contracts makes the question sharper rather than settling it.
Two facts pushed the decision this way rather than the other. `project.definition.md`'s
non-goal *no optimistic locking, last-write-wins* means a uniqueness rule could not be
enforced in the aggregate at all — two concurrent creations would each see no existing
contract and both succeed — so it would need a database constraint, and the MVP page's
**risk 6** is about precisely that volume and parallelism. And PD‑04 scoped out affiliated
contracts, which is the mechanism by which one employer legitimately holds several.
*If wrong:* a unique partial index on `(employer_id)` where status is `ACTIVE`, a failure
scenario in § 8, and a classification in § 9 — which GraphQL has no good word for, see the
note there. Deliberately **not** an aggregate check, because that is the thing
last-write-wins cannot protect.


## 4. Preconditions

- No aggregate need exist: this use case creates one.
- The employer referenced by `employerId` is assumed to exist, and the assumption is not
  checked (PD‑06). No source says the existence is checked at creation.
- The lessor referenced by `lessorId` is assumed to exist, on the same terms and for the same
  reason.


## 5. Flow

1. Accept the command from the inbound port.
2. Read `now` from `ClockPort` (PD‑08, PD‑11). Nothing time-related is taken from the input.
3. Construct the `MasterLeasingContract` together with the first version of its configuration
   (`version = 1`). The aggregate enforces its own invariants — construction either yields a
   valid contract or throws (`modelling.definition.md`;
   `adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`). Every rule in § 2.3 runs
   here, in a value object or in the factory.
4. The contract is created `ACTIVE` (PD‑07), with `creationTime` and `activationDate` both set
   to `now` (PD‑08).
5. Point the contract at the configuration created with it as the current one — the source
   defines `mlc_config_id` as "the current configuration version".
6. Persist the aggregate through its outbound port.
7. Publish a domain event, by calling the `DomainEventPublisher` outport inside the same
   `@Transactional` method — `adr/0002-domain-event-publication.adr.md` for the call site, and
   `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md` for what the
   adapter behind it does: it writes the outbox row synchronously, in this transaction, and
   performs no outbound call. **Both the event's name and its payload are derived, not
   sourced** — no source names any event or says what one carries. `adr/0019` requires a spec
   to name the events it emits and `modelling.definition.md` fixes past tense, so
   `MasterLeasingContractCreated` is coined here rather than adopted, and it becomes the
   outbox record's type and a consumer-visible contract. Its payload is derived to be **the
   contract's identifier and the moment of creation**, on the grounds that the dispatcher
   needs to find the contract and that a fact's own timestamp is part of the fact; AC‑04
   asserts that payload and cites this derivation rather than restating it.

> **The outbox adapter named in step 7 does not exist yet, and this spec does not build it.**
> `adr/0022` decides the mechanism; the adapter currently wired behind
> `DomainEventPublisher` is `LoggingDomainEventPublisher` — which, despite its name, logs
> nothing: see `documentation/ports/domain-event-publisher.outport.spec.md` § 4, which owns
> that statement. So step 7's call site
> is implemented exactly as specified and its *delivery guarantee* is not yet real.
>
> This is scoped out deliberately rather than by omission. Building the outbox means a table,
> an adapter, a scheduled relay and its own failure semantics — that is an increment with its
> own spec, and `adr/0019` already directs the record's content to an outbound port spec. What
> this use case owes is that the event goes through the port inside the transaction, which is
> what AC‑04 asserts and all `adr/0022` needs from a caller.
>
> § 10 carries this as a **named deferral with an owner**, not as a ticked box. Compare
> `HANDOFF.md` § 11: *an absence nobody wrote down reads as verified.*
>
> **A record of what this increment corrected, kept because the failure recurred inside the
> correction.** `DomainEventPublisher`'s KDoc used to say the implementation "is responsible for
> delivering the event after commit (ADR 0002)" — which `adr/0022` superseded without
> propagating. Corrected here, together with the port spec's § 2 through § 5, which had been
> left asserting the opposite of the KDoc.
>
> The correction then reproduced the defect **three more times**: a KDoc claiming the adapter
> "logs", inferred from the class name and never read against the class; and this spec repeating
> that claim in two further places while describing the fix. Each was caught by a *later*
> `ddd-hex-reviewer` round, never by the one before it — five instances of one failure class in
> a single increment, three of them inside the correction.
>
> The mechanism, rather than more care: **cite the owning document, do not paraphrase it.**
> `file-usage.definition.md` § 4 now says so in those words. Every statement here about what the
> adapter does points at port spec § 4 instead of restating it, because a paraphrase is a copy
> that can go stale — and five of them did.


## 6. Side Effects

- **Persistence:** one master leasing contract with its first configuration. `adr/0019`'s
  decision is that a use case changing a contract writes its own state **and an outbox
  record** in the same transaction; the record is not written yet, per § 5's deferral note,
  and the MVP page's scope item *"Synchronisation der Daten mit Radar und Odoo für die Use
  Cases"* is what it will eventually serve.
- **Event publication:** one event (§ 5 step 7), published inside the transaction through the
  outport.
- **Outbound calls to foreign systems:** none. This use case calls neither Radar nor Odoo and
  does not know they exist; the dispatcher does (`adr/0019`). PD‑03 and PD‑06 are the two
  decisions that kept it that way. It has outbound ports for persistence and for the clock,
  which is a different thing from calling out to another system.


## 7. Acceptance Criteria

> Eight criteria. Four are the ones that survived the blocked revision; four exist because a
> PD closed the question that had prevented them. Each of the new four **names the PD it
> rests on**, so that a PD being overturned points straight at the criteria that change.

**AC-01 – A contract is created with exactly one current configuration**
Given a command carrying an employer reference and a set of terms
When the use case runs
Then the aggregate handed to the outbound port points at exactly one configuration as
current, and that configuration's version is `1`.
*(Observed at the driver against the same fake repository as AC‑02. "Exists" was the previous
wording and it named no observation point — with no read side it could have meant either the
driver's capture or a persisted re-read, which are two different tests.)*

**AC-02 – The contract carries the terms it was created with**
Given a command carrying the configuration fields listed in § 2.2
When the use case runs
Then the aggregate handed to the outbound port carries those values unchanged.
*(Observed at the driver, against a fake repository that captures what it was given.
`test.definition.md` § 9 maps a use case test to an `AC-NN`; the persistence roundtrip is a
separate concern, covered by an integration test in § 10, and the payload cannot serve — § 3
returns identifiers only and this service has no read side.)*

**AC-03 – A contract cannot exist without an employer**
Given a command with a blank employer reference
When the use case runs
Then no contract is created and `InvalidMasterLeasingContractException` is raised.
*(Rests on the labelled derivation in § 2.3 — the one rule that is argued from the sources
rather than decided by a PD.)*

**AC-04 – Creation is announced exactly once**
Given a successful creation
When the use case completes without error
Then exactly one domain event is published through the outport, and its payload is the one
derived in § 5 step 7.
*(Observed at the driver against a fake publisher. Cardinality and payload only: that the
adapter behind the port writes an outbox row in the same transaction is `adr/0022`'s
guarantee, it is not yet implemented (§ 5's deferral note), and when it is it will be checked
where it lives — an integration test over the adapter — not by asserting infrastructure
through a use-case test.)*

**AC-05 – A contract is created active** *(rests on PD‑07)*
Given a valid command
When the use case runs
Then the aggregate handed to the outbound port has status `ACTIVE`, and the returned payload
reports it.

**AC-06 – The system dates the contract, not the caller** *(rests on PD‑08, PD‑11)*
Given a valid command
When the use case runs
Then `creationTime` and `activationDate` on the aggregate handed to the outbound port both
equal the instant returned by `ClockPort`.
*(The complementary half — that no timestamp can reach the input at all — is structural
rather than behavioural, and is asserted by `TimestampRulesTest` over the adapter packages
rather than by a use-case test. AC‑05 of the previous revision was deleted for lacking a
subject; this one has one because PD‑08 named it.)*

**AC-07 – A term that could not be meaningful is rejected** *(rests on PD‑10)*
Given a command whose `priceRangeMin` exceeds its `priceRangeMax`
When the use case runs
Then no contract is created and `InvalidMasterLeasingContractException` is raised.
*(One rule, named, rather than "input is validated". PD‑10 explains why the criterion is
deliberately narrow: the real validation matrix does not exist, and a criterion asserting
"validated" would read as though it did. The remaining § 2.3 rules are asserted at their
value objects, which is where they live.)*

**AC-08 – Optional terms may be absent** *(rests on PD‑01)*
Given a command carrying only the six fields PD‑01 makes mandatory
When the use case runs
Then a contract is created and no exception is raised.
*(The negative of AC‑07 and the direct assertion of PD‑01. It is the criterion that fails
first if PD‑01 is overturned toward more mandatory fields, which is the point of having it.)*


## 8. Failure Scenarios

- **Blank employer reference** — rejected, nothing persisted (AC‑03), classified
  `BAD_REQUEST` (§ 9).
- **A term violating a § 2.3 rule** — rejected, nothing persisted (AC‑07 for the price range;
  the rest at their value objects), classified `BAD_REQUEST`.
- **Persistence failure** — the transaction rolls back and no contract exists; classified
  `INTERNAL_ERROR`. Once the outbox adapter exists, the row rolls back with it, because
  `adr/0022` has it written in the same transaction, and nothing is dispatched. This was
  OQ 12's substance and is decided; it is not yet built (§ 5).

Not listed, deliberately:

- Any failure of a call to Radar or Odoo. This use case makes none (§ 6).
- A missing employer or lessor. Not verified (PD‑06).
- A missing parent contract. Affiliated contracts are out of scope (PD‑04).
- Concurrent creation of a second contract for the same employer. Permitted (PD‑05).


## 9. API Contract

### REST

`Not applicable — GraphQL is the only transport in the MVP
(adr/0020-graphql-as-the-only-transport.adr.md). REST arrives with the first machine consumer,
and springdoc with it.`

### GraphQL

Operation. **The operation's name and both type names are coined here, not sourced** — no
source names them. They follow the naming convention in `coding-style.definition.md`, and
because `adr/0020` makes the GraphQL schema *the* API contract for the Backoffice UI, they are
consumer-visible contract rather than notation: renaming one later is a breaking change for
that UI.

```graphql
mutation createMasterLeasingContract(input: CreateMasterLeasingContractInput!): CreateMasterLeasingContractPayload!
```

The input type's required (`!`) fields are exactly PD‑01's six. It carries **no timestamp
field** — PD‑11, enforced by `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes`.

Error classification:
- `BAD_REQUEST` — any rule in § 2.3 is violated (AC‑03, AC‑07)
- `INTERNAL_ERROR` — persistence failed

`NOT_FOUND` is not used: this operation creates rather than loads, verifies no participant
(PD‑06) and resolves no parent (PD‑04). Should PD‑05 be overturned to "one active contract per
employer", a conflict case appears — and GraphQL's vocabulary has no conflict type, so it
would collapse into `BAD_REQUEST`. That asymmetry belongs in the contract rather than in a
support conversation, so it is written here even though PD‑05 decided the other way.

### Executable requests

- `api/uc07-create-master-leasing-contract.graphql` — the happy path plus one request per
  classification above, plus a minimal request carrying only PD‑01's six mandatory fields
  (AC‑08). It also carries the payload's field list, which is § 3's one derived claim (§ 3).
- No `.http` file: REST is not applicable (`CLAUDE.md`, API Contract Documentation).


## 10. Definition of Done

> **This spec is workable.** Every question that blocked an implementable section is closed —
> nine by a provisional decision of ours (PD‑01 … PD‑10), one by an edit to
> `architecture.definition.md` (PD‑11), one by `adr/0023`, and OQ 12 by `adr/0022`.
> `sdd.playbook.md` § 4.2 is satisfied again: every `AC-NN` is cited by a DoD item below, and
> every item names the test that satisfies it (`test.definition.md` § 9). The blocked
> revision's departure from § 4.2 is over.

### Behaviour

- [ ] AC-01 covered by `CreateMasterLeasingContractDriverTest.create_savesAggregateWithExactlyOneCurrentConfiguration`
- [ ] AC-02 covered by `CreateMasterLeasingContractDriverTest.create_savesTermsUnchanged`
- [ ] AC-03 covered by `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenEmployerIdBlank`
- [ ] AC-04 covered by `CreateMasterLeasingContractDriverTest.create_publishesExactlyOneMasterLeasingContractCreated`
- [ ] AC-05 covered by `CreateMasterLeasingContractDriverTest.create_savesStatusActive`
- [ ] AC-06 covered by `CreateMasterLeasingContractDriverTest.create_usesClockPort_forCreationTimeAndActivationDate`
- [ ] AC-07 covered by `CreateMasterLeasingContractDriverTest.create_throwsInvalidMasterLeasingContractException_whenPriceRangeMinExceedsMax`
- [ ] AC-08 covered by `CreateMasterLeasingContractDriverTest.create_succeeds_whenOptionalTermsAbsent`
- [ ] The aggregate's own invariants covered by `MasterLeasingContractTest.create_setsStatusActive`,
      `MasterLeasingContractTest.create_pointsAtExactlyOneCurrentConfiguration`,
      `MasterLeasingContractTest.create_recordsMasterLeasingContractCreated` and
      `MasterLeasingContractTest.pullDomainEvents_returnsEmptyList_onSecondCall`
- [ ] Every § 2.3 rule enforced by a value object has a domain test: `EmployerIdTest.blankThrows`,
      `LessorIdTest.blankThrows`, `PartnerNumberTest.blankThrows`, `ContractTypeTest.blankThrows`,
      `CreditLimitTest.zeroThrows`, `CreditLimitTest.negativeThrows`,
      `CurrencyCodeTest.lowercaseThrows`, `CurrencyCodeTest.wrongLengthThrows`,
      `EligibleEmployeesTest.zeroThrows`, `PercentageTest.aboveHundredThrows`,
      `PercentageTest.negativeThrows`, `PriceRangeTest.minAboveMaxThrows`,
      `PriceRangeTest.negativeBoundThrows`, `PriceRangeTest.of_throwsInvalidMasterLeasingContractException_whenOnlyOneBoundPresent`,
      `PriceRangeTest.of_returnsNull_whenBothBoundsAbsent`
- [ ] The two § 2.3 rules that guard a plain `Int` rather than a value object covered by
      `ContractConfigurationTest.create_throwsInvalidMasterLeasingContractException_whenEarlyClaimWindowMonthsNegative`
      and `ContractConfigurationTest.create_throwsInvalidMasterLeasingContractException_whenServicePackageVersionBelowOne`.
      They sit in the entity's factory because a one-field value object per bounded `Int` would
      add two types that carry no meaning beyond their range
- [ ] The persistence roundtrip covered by `MasterLeasingContractJpaRepositoryIT.save_thenFindById_roundTripsEveryTerm`
      and `MasterLeasingContractJpaRepositoryIT.save_persistsOptionalTermsAsNull_whenAbsent`
- [ ] The GraphQL adapter's mapping covered by
      `MasterLeasingContractGraphQLControllerTest.createMasterLeasingContract_mapsInputToCommand`
      and `MasterLeasingContractGraphQLControllerTest.createMasterLeasingContract_mapsResultToPayload`
- [ ] § 8's `INTERNAL_ERROR` classification covered by
      `MlcGraphQLExceptionResolverTest.resolveToSingleError_leavesAnInfrastructureFailureToSpring`,
      and § 9's absent `NOT_FOUND` by
      `MlcGraphQLExceptionResolverTest.resolveToSingleError_hasNoNotFoundClassification`.
      Added because `conformance-reviewer` found a specified failure scenario with zero
      executable evidence — § 8 named the classification and nothing asserted it
- [ ] § 8's "nothing is dispatched" covered by
      `CreateMasterLeasingContractDriverTest.create_doesNotPublish_whenPersistenceFails`. The
      driver's `save`-before-`publish` ordering is load-bearing under `adr/0022`, and reversing
      the two lines would have broken nothing else
- [ ] The read path accepts values the write path rejects, so a tightened **PD-10** cannot make
      an already-signed contract unloadable — covered by
      `ReconstitutionTest.reconstitute_acceptsACurrencyTheWritePathRejects`,
      `ReconstitutionTest.reconstitute_acceptsAPercentageAboveTheCurrentCeiling`,
      `ReconstitutionTest.reconstitute_acceptsACreditLimitTheWritePathRejects`,
      `ReconstitutionTest.reconstitute_acceptsAnInvertedPriceRange`,
      `ReconstitutionTest.reconstitute_acceptsBlankValuesTheWritePathRejects` and — for the other
      half — `ReconstitutionTest.invoke_stillValidates_soReconstituteIsTheOnlyWayPast`.
      Added because `ddd-hex-reviewer` found that two specs claimed this property while the
      mapper validated everything on read

### Structure

- [ ] `mlc` is a row in `architecture.definition.md` § 11, citing `adr/0015`, and the package
      exists — the registry is parsed in both directions, so both or neither
      (`ContextRegistryTest.topLevelPackagesMatchTheRegistry`), and the package follows the
      standard ontology (`ContextRegistryTest.everyContextFollowsTheSameOntology`)
- [x] PD‑11: `architecture.definition.md` § 8.1's timestamp table carries an `inbound.graphql`
      row, and it has an executable owner rather than a reading —
      `TimestampRulesTest.noTimestampFieldOnExternalTransportInputTypes`
- [x] `adr/0023-participant-identities-are-context-local.adr.md` decides whether `EmployerId`,
      `LessorId` and `PartnerNumber` become shared Value Objects — reserved to an ADR by
      `adr/0005`'s Consequences. It decides **not**, and says on what test
- [x] OQ 12 closed: `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md`
      decides the mechanism, and `adr/0002`'s `## Status` is unchanged — that ADR's decision was
      never in conflict, only its reference adapter
- [ ] `DomainEventPublisher`'s KDoc no longer claims after-commit delivery, which `adr/0022`
      superseded (§ 5's deferral note)

### Contracts

- [ ] `api/uc07-create-master-leasing-contract.graphql` covers every classification in § 9,
      carries the payload's field list (§ 3), and includes the PD‑01-minimal request (AC‑08)
- [ ] No `api/uc07-*.http` exists, because § 9 states REST is not applicable
- [ ] A domain spec for the aggregate exists in `documentation/domain/`
- [ ] Port specs in `documentation/ports/` reflect the ports as implemented — the inport
      triple and the repository outport

### Governance

- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` on the architecture axis
- [ ] `conformance-reviewer` matches every § 7 criterion to a test and every § 10 box to an
      artifact
- [ ] Quality gates green (`test.definition.md` § 7)

### Deferred, with an owner — not boxes, and not silently absent

`HANDOFF.md` § 11: *an absence nobody wrote down reads as verified.* These are out of this
use case's scope, and each says who owns it.

- **The outbox adapter and its relay** (`adr/0019`, `adr/0022`). Its own increment, with its
  own spec and an outbound port spec for the record's content, as `adr/0019` directs. What
  this use case owes `adr/0022` is the call site, and AC‑04 asserts it. Until then the row is
  not written, and the wired adapter delivers post-commit instead — see
  `documentation/ports/domain-event-publisher.outport.spec.md` § 3, which owns that statement.
- **The outbox row's atomicity with the aggregate.** *"The row commits with the contract or not
  at all"* is `adr/0022`'s guarantee and cannot be exercised before the adapter exists.
  **Owner: the outbox increment**, which also owes UC06 a migration note: `TourStartedListener`
  depends on post-commit delivery by design, and swapping the adapter changes when it runs
  (`documentation/ports/domain-event-publisher.outport.spec.md` § 3).

  **The aggregate's own rollback is not deferred — it is UC07's, and it is covered.** That
  distinction was wrong here for one round. The deferral originally read "the atomicity claim is
  `adr/0022`'s" and so assigned the whole of § 8's third bullet to a future increment;
  `conformance-reviewer` pointed out that the plain claim *"the transaction rolls back and no
  contract exists"* is this driver's own `@Transactional` boundary, needs no outbox to exercise,
  and was being pushed onto work that does not own it. It is now asserted by
  `MasterLeasingContractRollbackIT.create_persistsNothing_whenTheContractRowViolatesAConstraint`.
  What stays deferred is only the outbox row travelling with it.
- **Authentication.** `adr/0021` excluded it deliberately; PD‑09 is what lets this use case
  proceed without it, by reading `owner` as a team rather than as a principal. If PD‑09 is
  overturned, this use case acquires that dependency.
- **The Miro board.** Unread, and the replacement for PD‑01, PD‑02, PD‑04 and PD‑10 when a
  human opens it. § 1 records it as outstanding rather than claiming it said nothing.
- **The eleven questions themselves.** Closed by us, not by the project.
  `documentation/notes.md` keeps them in the form they should be *asked*, and every PD above
  states what changes when a real answer arrives. Closing a PD with a source is a Spec Phase
  change, not a loop iteration.
