# Project Vision: JRL Contract Management

## What this document is

The **highest-ranked document** in `CLAUDE.md`'s authority order. Every agent run opens by
reading it, so it describes the service being built — not the process used to build it, and
not the example still sitting in the tree.

| Question | Answered in |
|----------|-------------|
| What is this repository? | `README.md` |
| What is the service for? | **this file** |
| How is work done here? | `CLAUDE.md`, then the two playbooks |

Sources are the `JCM` Confluence space (*JRL Contract Management*). Where this document
states a scope decision, that decision comes from the **MVP** page; where it states a field
or an entity, that comes from the **Contract Management Domain Data model** page. Claims
with no source there do not belong in this file.


## Purpose of the service

JobRad's leasing business runs on two levels of contract. A **master leasing contract**
(*Leasing­rahmenvertrag*, LRV) is agreed with an employer and governs the terms under which
that employer's staff may lease. Each **individual leasing contract**
(*Einzel­leasing­vertrag*, ELV) is one employee's bike, issued under that master contract
and inheriting its conditions.

Today this work happens by hand across Radar, Odoo and a set of submenus, and the rules
connecting the two levels — limits, entitlements, status dependencies — exist only in the
heads of the people doing it. This service exists to make those rules **explicit,
executable and checkable**.

The near-term product is a backend for the internal Backoffice UI used by contract
administrators. It is not a customer-facing system and it is not a replacement for the
leasing platform.


## Domain in one page

| Entity | German | What it is |
|--------|--------|-----------|
| `MasterLeasingContract` | Leasingrahmenvertrag / LRV | The framework contract with an employer |
| `MlcConfiguration` | — | One version of that contract's terms: credit limit, return quota, price range, service packages |
| `IndividualLeasingContract` | Einzelleasingvertrag / ELV | One employee's bike lease, issued under an LRV |
| `IlcConfiguration` | — | One version of that lease's terms: rate, duration, residual value, service tier |
| `ServiceAgreement` | Dienstleistungsvertrag / DLV | The administrative agreement linked to a master contract |
| `UsageProvisionContract` | Nutzungsüberlassungsvertrag / ÜV | The employer-employee side of a lease: conversion rate, subsidy, monetary benefit |
| `Document` | — | A pointer to a contract PDF held in the external DMS |

Two properties of this model drive most of the design. **Terms are versioned separately
from the contract they belong to**, and an inheriting configuration records whether the
inherited values stay live-linked to the parent or were copied once (`inheritance_mode`).
That flag is an integration contract between the two levels, not a field — see
`adr/0015-two-contexts-by-contract-level.adr.md`.


## Bounded contexts

Two, split by contract level. `mlc` owns the master contract, its configuration and the
service agreement; `ilc` owns the individual lease, its configuration and the usage
provision contract.

The split is not organisational tidiness. The same word means different things on each
side: an LRV's *Kündigung* is an employer leaving, which must consider every lease issued
underneath it; an ELV's *Beendigung* is a 36-month term running out and a bike being sold.
One `Status` type covering both would be a type that permits states its own aggregate
cannot reach.

The registry in `architecture.definition.md` § 11 is authoritative and parsed. This
paragraph is not the registry.


## What this service owns, and what it does not

Radar and Odoo are the leading systems today. That is a fact about the platform, not a
temporary inconvenience, and it decides where this service's authority ends.

| Ours | Not ours |
|------|----------|
| The contract: its existence, status, lifecycle transitions and cancellation grounds | The employer (`employer_id`) |
| Its terms: limits, quotas, rates, durations, residual values | The employee (`job_cyclist_id`) |
| The link between an LRV and the ELVs issued under it | The bike (`bike_id`) |
| The rules connecting the two levels | The lessor, the service provider, the partner number |

Everything in the right column arrives through an **anti-corruption layer**: an adapter
that translates a foreign representation into this service's value objects. No Odoo or
Radar structure reaches the domain. The data model page already draws this line — every
entity in the right column is marked *external* there — and `adr/0017-contract-data-ownership-boundary.adr.md` records it.

The practical consequence is that an aggregate here holds real invariants rather than
mirroring a foreign record. A master contract cannot be terminated while leases are active
under it; a lease cannot be issued outside its master contract's price range or beyond its
credit limit. Those are the rules the discovery phase recorded as *not currently expressed
anywhere*.


## MVP scope

From the **MVP** page. A thin slice through both contract levels: the standard case first,
special cases iteratively.

| Use case | Level | Today |
|----------|-------|-------|
| Create a master contract (trigger mocked; eventually employer onboarding) | LRV | — |
| Create an individual lease and link it to its master contract | ELV | — |
| Regular termination of a master contract with no active leases | LRV | Odoo |
| End of lease — sale to JobRad GmbH (~5 000 / year) | ELV | Radar, Odoo |
| Name change, e.g. on marriage | ELV | Radar, by hand |

Displays are in scope too — conditions and limits, the contract PDF, the linked service
agreement, the list of leases under a master contract. They need a read side, which this
repository has never had; see Non-Goals.

Deliberately **out** of the first iteration, from the same page: terminating a master
contract that still has active leases, retrospective changes, arbitrary status changes,
other company changes, early lease termination, object or lessee swaps, follow-on leases,
the refinancing interface, and the complete Radar and Odoo interfaces.


## Architectural stance

- Strict Ports & Adapters, with a framework-free core
- Aggregates enforce their own invariants; no setters, no `data class` on an entity
- **Lifecycle transitions belong to the aggregate.** No state-machine framework — a guard
  that reaches into another context is application logic hiding in configuration
  (`adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`)
- One use case = one transaction boundary, owned by the driver
- Contexts communicate through `shared.domain.event` or a published inport, never internals
- Persistence is an implementation detail; the domain holds no persistence annotations
- Outbound synchronisation to Radar and Odoo goes through an **outbox**, never a foreign
  call inside our transaction (`adr/0019-outbound-synchronisation-through-an-outbox.adr.md`)
- **One deployable** for the MVP, though the platform's component view draws two services.
  The dependency between the two levels is the least understood thing in the domain, and a
  network boundary across it would turn a failing test into a data divergence (`adr/0016-single-deployable-for-the-mvp.adr.md`)


## Development doctrine

- Spec first. No implementation without one.
- ADR before an architectural change.
- No production code without a failing test.
- Small, verifiable increments.
- When a rule rots, give it an executable owner rather than restating it.

Specifications are written in English against German sources. The translation is therefore
part of the spec, and a disputed term is resolved by going back to the Confluence page, not
by re-reading the spec.


## Non-Goals

Absences, written down so that nobody mistakes one for a decision and invents an answer
three different ways.

| Absent | Consequence you should know about |
|--------|-----------------------------------|
| **No read side yet** | The repository has no query ports, no projections, no read model. The MVP's display requirements need one, and choosing the pattern is an **ADR the first display use case must write**. The three use cases sequenced ahead of it are all writes, which is why this is a written trigger rather than a blocker |
| **No REST** | GraphQL is the only transport. The platform's frontends all speak Apollo; REST — and the OpenAPI description of it — arrives with the first machine consumer (`adr/0020-graphql-as-the-only-transport.adr.md`) |
| **No authentication and no authorization** | Neither is implemented. `adr/0021-operational-baseline-from-the-platform.adr.md` adopts the operational baseline and deliberately stops short of authentication, because verifying a token is production code and arrives behind a failing test and a spec; the intended shape is token verification with **no roles**, since a platform-wide roles and permissions concept does not exist yet — a workshop is pending, and inventing three roles here would be inventing the wrong three. This row previously read as though authentication already existed, which contradicted the ADR |
| **No live inbound sync** | Nothing subscribes to Radar or Odoo. Reading a real contract out of Radar is a one-off migration exercise, not a running integration |
| **No document storage** | Contract PDFs live in the external DMS (d.velop). This service holds a pointer and, in the MVP, a stub behind the port |
| **No employer onboarding** | The trigger that creates a master contract is mocked. The real one is the onboarding service, whose message format is not documented anywhere yet — so it is not guessed at (`adr/0019-outbound-synchronisation-through-an-outbox.adr.md` port spec) |
| **No optimistic locking** | No `@Version`. Concurrent updates are last-write-wins |
| **No audit trail as a feature** | The outbox gives it a foundation; nothing consumes it |

Also not goals: a microservice split before the domain is understood, premature scalability
patterns, and coverage thresholds as merge gates.


## The example still in the tree

`booking` and `guide` — a tour-booking service — are inherited from the template this
repository grew out of. They are the only code the 27 ArchUnit rules and the documentation
gates currently have to check, so they stay until the first Contract Management context is
complete end to end, and then leave in one commit along with `adr/0003-separate-guide-bounded-context.adr.md`.

They are not a reference for how this domain should look. When they contradict this
document, this document wins and they are what is stale.


## What "done" means

Not "contract management is complete" — it will not be. Done, per increment, is what
`test.definition.md` § 7 says: every gate green, including the ones that read the
documentation, and every Definition of Done box in the use case's spec pointing at an
artifact that exists.
