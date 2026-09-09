# Project Vision: Contract Management

## What this document is

The **highest-ranked document** in `CLAUDE.md`'s authority order. Every agent run opens by
reading it, so it describes the service being built — not the process used to build it.

| Question | Answered in |
|----------|-------------|
| What is this repository? | `README.md` |
| What is the service for? | **this file** |
| How is work done here? | `CLAUDE.md`, then the two playbooks |


## Purpose of the service

A **CRUD API over a Master and the Contracts it holds.** A Master is a party this business
contracts with; each Contract under it is one agreement with a period and a monthly amount.
The API creates, reads, updates and deletes Masters, and adds and removes Contracts on them.

That is the whole domain, and the scope is deliberately smaller than the machinery around it.

**Read this next, because it decides how to interpret everything else here.** The subject of
this repository is the *method* — spec-driven development with executable gates — and the
domain is the specimen it is practised on. A domain drawn from a real product would drag in
questions nobody in this repository can answer, and the previous version of this document
proves it: it described a leasing business, and the one specification written against it was
blocked on eleven questions that had to be settled by people outside the codebase. Nothing
about the *method* was learned while waiting.

So the domain here is invented, and small enough to hold in one paragraph, on purpose. The
gates, the ADR discipline, the review agents and the two loops are the real subject matter.

**The consequence to keep in view:** an invented domain cannot settle a design argument by
appeal to reality. Where a rule below looks arbitrary — the shape of a customer number, a cap
of fifty contracts — it *is* arbitrary. It is there because an Always-Valid aggregate needs
invariants to enforce and a test needs something to assert, and it is chosen rather than
discovered. Do not reason from these rules to what a real contract system should do, and do
not defend one by claiming a source it does not have.


## Domain in one page

Two types. One aggregate.

| Type | Role | Fields |
|------|------|--------|
| `Master` | Aggregate root | `id`, `name`, `customerNumber`, `status`, `createdAt` |
| `Contract` | Entity **inside** the Master aggregate | `id`, `contractNumber`, `period`, `monthlyAmount` |

`Contract` is an entity and not a Value Object because it has an identity that outlives a
change to its fields. It is **not** an aggregate root: it has no independent existence, it is
loaded and saved with its Master, and it is never addressed without naming that Master.
`adr/0024-one-context-with-master-as-the-aggregate-root.adr.md` argues why, and the short
version is that every invariant worth writing spans the two.

Three invariants are the reason the boundary sits where it does:

- A contract number is **unique within its Master**. Two Masters may each hold a `C-0001`.
- A Master holds **at most fifty** contracts.
- A contract may be added only to an **ACTIVE** Master.

None of the three can be enforced by a `Contract` looking only at itself, and none can be
enforced honestly outside an aggregate in a service with no optimistic locking — see
Non-Goals. That is the whole argument for the aggregate boundary, stated once, here.


## Bounded contexts

**One**, named `contract`. `architecture.definition.md` § 11 is the authoritative registry and
is parsed by a test; this paragraph is not the registry.

One context means the cross-context rules — published inports, domain events between
contexts, the closed-internals rule — have **no subject in this codebase**. That is a real
loss for a repository whose purpose is demonstrating the method, and it is accepted rather
than fixed by inventing a second context. A context with no reason is drift with a row in a
table. `adr/0024` records the trade explicitly; `adr/0008`, though withdrawn, is kept as the
worked example of the integration pattern this service no longer has.


## Architectural stance

- Strict Ports & Adapters, with a framework-free core
- Aggregates enforce their own invariants; no setters, no `data class` on an entity
- **Lifecycle transitions belong to the aggregate.** No state-machine framework
  (`adr/0018-lifecycle-transitions-belong-to-the-aggregate.adr.md`)
- One use case = one transaction boundary, owned by the driver
- Persistence is an implementation detail; the domain holds no persistence annotations
- **Reads use the same repository outport the writes use** — no query side, with three
  written conditions for revisiting that
  (`adr/0025-reads-go-through-the-repository-outport.adr.md`)
- Time enters through `ClockPort` at the driver. A GraphQL client may not supply a timestamp,
  and a test enforces it (`architecture.definition.md` § 8.1)


## Scope

| Use case | Spec |
|----------|------|
| Create a Master | `uc01-create-master.spec.md` |
| Get a Master with its Contracts | `uc02-get-master.spec.md` |
| Update a Master's name and status | `uc03-update-master.spec.md` |
| Delete a Master and its Contracts | `uc04-delete-master.spec.md` |
| Add a Contract to a Master | `uc05-add-contract-to-master.spec.md` |
| Remove a Contract from a Master | `uc06-remove-contract-from-master.spec.md` |

Deliberately **out**: listing or searching Masters, paging, updating a Contract in place
(remove and re-add), contract periods that may not overlap, and any second party to a
contract.

Updating a Contract in place is the omission most likely to be mistaken for an oversight, so:
it is left out because it adds a third mutation shape without adding a new *kind* of rule to
enforce, and the six above already cover create, read, update, delete, and both directions of
collection membership.


## Development doctrine

- Spec first. No implementation without one.
- ADR before an architectural change.
- No production code without a failing test.
- Small, verifiable increments.
- When a rule rots, give it an executable owner rather than restating it.


## Non-Goals

Absences, written down so that nobody mistakes one for a decision and invents an answer three
different ways.

| Absent | Consequence you should know about |
|--------|-----------------------------------|
| **No REST** | GraphQL is the only transport; REST and its OpenAPI description arrive with the first machine consumer (`adr/0020-graphql-as-the-only-transport.adr.md`). `api/` therefore holds only `.graphql` files |
| **No authentication and no authorization** | Neither is implemented. Every GraphQL operation is unauthenticated, which is fine for a service with no data and is not fine the moment one runs anywhere shared. `adr/0021` adopts the operational baseline and deliberately stops short, because verifying a token is production code and arrives behind a failing test and a spec |
| **No query side** | Reads go through the write repository. This is now a **decision** rather than an absence — `adr/0025` names the three conditions that reopen it |
| **No optimistic locking** | No `@Version`. Concurrent updates are last-write-wins, and this is why `customerNumber` is *not* globally unique: two concurrent creates would both read "no such customer number" and both succeed, so the constraint would be advisory. Uniqueness that holds is uniqueness inside one aggregate |
| **No outbound integration** | Nothing is synchronised anywhere. Domain events are published through `DomainEventPublisher` and logged; nothing consumes them |
| **No audit trail** | Domain events are the foundation for one; nothing builds on it |
| **No soft delete** | Deleting a Master removes it and its Contracts. `status` is about whether a Master is trading, not whether it exists |

Also not goals: a microservice split before the domain is understood, premature scalability
patterns, and coverage thresholds as merge gates.


## The state of the tree

The service has **one registered bounded context, `contract`**, and the first slice of domain
code inside it. The tour-booking example inherited from the template and the earlier leasing
implementation were deleted together; UC01 is now being implemented against the six
specifications above, and UC02–UC06 are written but not started.

That state is transient and is not silent, and the arrangement that keeps it honest is
`adr/0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md`
(superseding `adr/0026`). It draws a line between two kinds of rule that match nothing:

- A rule with no subject **yet** — the layers of the `contract` slice that are still unbuilt —
  is allowed to pass empty behind one named allowance, `whileTheContractSliceIsIncomplete`,
  and a tripwire that fails once every awaited package exists.
- A rule with no **possible** subject — five rules describing the `inbound.rest` and
  `inbound.listener` packages that `adr/0020` leaves the service without — was **deleted**, on
  the written condition that it is restored in the increment that gives it one. A second
  tripwire fails on the commit that creates either package.

The distinction is the correction `adr/0027` makes to `adr/0026`, which bundled both into one
allowance whose precondition could therefore never expire.


## What "done" means

Not "contract management is complete". Done, per increment, is what `test.definition.md` § 7
says: every gate green, including the ones that read the documentation, and every Definition
of Done box in the use case's spec pointing at an artifact that exists.
