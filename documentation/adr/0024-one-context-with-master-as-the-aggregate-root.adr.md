# ADR 0024 – One Bounded Context, with Master as the Aggregate Root and Contract Inside It

## Status
Accepted

## Context

`adr/0015-two-contexts-by-contract-level.adr.md` split the service into `mlc` and `ilc` by
contract level. That split, the JCM data model it was drawn from, and the `mlc`
implementation built on it were all removed in one increment: the domain the service is now
being built for is a plainly-scoped CRUD API over a **Master** that owns several
**Contracts**, and nothing in it corresponds to the master/individual lease distinction
ADR-0015 was reasoning about.

So the context question is open again, and it has to be answered before any package exists —
`architecture.definition.md` § 11 is parsed in both directions, and a row without a package
fails as loudly as a package without a row.

Two questions are actually being asked at once, and they are usually confused:

1. How many **bounded contexts** are there?
2. Is `Contract` an **aggregate root** of its own, or an entity inside the `Master` aggregate?

They are independent. A single context can hold several aggregate roots, and two contexts can
each hold one.

## Decision

**One bounded context, named `contract`.** Package
`com.example.contractmanagement.contract`, following the `core` / `inbound` / `outbound`
ontology every context follows (§ 3).

**`Master` is the only aggregate root.** `Contract` is an **entity inside the Master
aggregate**. It has an identity of its own, so it is an entity and not a Value Object, but it
has no independent existence: it is created, modified and removed through its Master, and it
is loaded and saved as part of it.

Consequences that follow directly, and are stated so they are not re-argued:

- There is **one repository outport**, `MasterRepository`. There is no `ContractRepository`,
  and adding one would be drift, not convenience.
- A contract is never addressed by its id alone at the boundary. Every operation naming a
  contract also names its master.
- Deleting a Master deletes its contracts with it. That is a property of the aggregate
  boundary, not a separate cascade decision.
- The § 11 row for `contract` lands in the **same increment as the package**, not before.

## Rationale

**The aggregate boundary is the transaction and invariant boundary, and the invariants here
are the Master's.** "A master's contract numbers are distinct", "a master may hold at most N
contracts", "removing the last contract is permitted" — every rule worth writing spans the
Master and its contracts together. Making `Contract` a root would put those rules outside any
aggregate, where the only way to enforce them is a check in a driver that two concurrent
callers can both pass. `project.definition.md` already records last-write-wins and no
optimistic locking as non-goals, which is exactly the setting where an invariant enforced
outside an aggregate is decoration.

**A second context would have nothing to keep apart.** § 11's own guidance says the pressure
to add a context is only justified by "a genuinely different ubiquitous language with its own
invariants and lifecycle, where sharing the model would force one aggregate to serve two
meanings". Master and Contract share one language, one lifecycle and one consumer. ADR-0015
could argue the opposite for `mlc`/`ilc` because a master leasing contract and an individual
lease had different owners, different consumers and different lifetimes; none of that
survived into this scope.

**The cost of being wrong is asymmetric, and it points this way.** Splitting one aggregate
into two later is a mechanical refactor with a test suite to lean on. Merging two aggregates
after code has come to rely on their independent lifecycles is not — it means retro-fitting
an invariant across data that has already been allowed to violate it.

**What this deliberately does not exercise.** With one context, the cross-context rules in
§ 11 rule 3 — published inport, domain events, no internal imports — have no subject here.
That is a real loss for a repository whose purpose is demonstrating the method, and it is
accepted rather than solved by inventing a second context. A rule with no subject is honest;
a context with no reason is drift with a row in a table.

## Consequences

- `architecture.definition.md` § 11 gains one `Bounded Context` row, `contract`, citing this
  ADR, in the increment that creates the package.
- `EmptyServiceTripwireTest` fails on that same increment, which is what re-arms the sixteen
  architecture rules currently allowed to match nothing (`adr/0026`).
- No cross-context integration exists, so `shared.domain.event` carries no cross-context
  event and `shared` holds no shared identity. `adr/0005`'s reservation — a second shared
  identity requires an ADR — is untouched and still unspent.
- If `Contract` ever acquires a lifecycle independent of its Master — its own status
  transitions, its own consumers, direct addressability — that is a new ADR promoting it to a
  root, and this one is superseded rather than quietly outgrown.
