# ADR 0015 – Two Bounded Contexts, Split by Contract Level

## Status
**Superseded by `adr/0024-one-context-with-master-as-the-aggregate-root.adr.md`.**

The `mlc`/`ilc` split was drawn from the JCM contract-level distinction. That domain, its data
model and the `mlc` implementation built on it were all removed together, and the service is
now scoped to a Master owning several Contracts — which has no master/individual level to
split on. ADR-0024 answers the same question for the current scope and explains why one
context is the right count here and two was the right count there.

## Context

JobRad's leasing business has two levels of contract. A master leasing contract
(*Leasingrahmenvertrag*, LRV) is agreed with an employer; each individual leasing contract
(*Einzelleasingvertrag*, ELV) is one employee's bike, issued under that master contract.

The data model page in the `JCM` space models them as separate entities with separate
configuration entities — `MASTER_LEASING_CONTRACT` + `MLC_CONFIGURATION`,
`INDIVIDUAL_LEASING_CONTRACT` + `ILC_CONFIGURATION` — each versioning its own terms, and each
carrying an `inheritance_mode` describing whether inherited values stay live-linked to the
parent or were copied once.

They are also tightly coupled. A master contract's credit limit and return quota constrain
the leases issued under it; the discovery phase recorded the LRV → ELV dependency rules as
the project's first risk, with the note that they are *not currently expressed anywhere*.

That coupling is the argument usually made for keeping them together. It is also the
argument for the opposite.

## Decision

Two bounded contexts, split by contract level:

| Package | Owns |
|---------|------|
| `mlc` | `MasterLeasingContract`, its configuration, the service agreement (DLV) |
| `ilc` | `IndividualLeasingContract`, its configuration, the usage provision contract (ÜV) |

Neither may import the other's internals. The dependency between the levels crosses as a
published inport or a domain event, per `architecture.definition.md` § 11 rule 3.

Registration in that registry happens when the packages are created, not here — the
registry is parsed in both directions, so a row without a package fails the build exactly
as a package without a row does.

## Rationale

**The same word means different things on each side.** An LRV's *Kündigung* is an employer
leaving, and it must consider every lease issued underneath. An ELV's *Beendigung* is a
36-month term running out and a bike being sold to JobRad GmbH. The cancellation grounds
differ, the statuses differ, and the configurations share almost no fields — one holds
credit limits and return quotas, the other rates, durations and residual values. A single
`Status` type spanning both would permit states its own aggregate cannot reach, and a
single configuration type would be a union with two disjoint halves.

**Coupling is the reason to draw the boundary, not to skip it.** The LRV → ELV rules are
the least understood part of this domain. Inside one package they would be expressed as
ordinary field access and would remain invisible: nothing would mark where a lease reaches
into its master contract. Across a context boundary they must be named — an inport, a
signature, a spec — and `DependencyRulesTest` fails on anything else.

**`inheritance_mode` is an integration decision wearing a field's clothes.** "Values stay
live-linked to the parent" and "values were copied once" are two different integration
patterns between two owners. A boundary makes that a design question with an owner; no
boundary makes it a nullable column.

## Consequences

- Cross-level rules cost more to write. A lease cannot read its master contract's credit
  limit directly; it asks through a port. That is the intended price.
- The registry gains two rows, each requiring this ADR as its justification.
- `shared` will grow the identity types that cross the boundary — following
  `adr/0005-bounded-context-identity-boundaries.adr.md`, a foreign identity crosses as an
  opaque value, so `ilc` holds a `MasterLeasingContractId` and not an `MlcConfiguration`.
- If the two levels later split into separate deployables
  (`adr/0016-single-deployable-for-the-mvp.adr.md`), this boundary is where the split runs,
  and it is already enforced.
