# ADR 0003 – Separate Individual-Leasing Bounded Context

## Status

Accepted

## Context

The Contract Management data model has two contract levels:
`MASTER_LEASING_CONTRACT` (LRV) and `INDIVIDUAL_LEASING_CONTRACT` (ELV), each with
its own configuration table, and the individual contract holds a foreign key to the
master contract and another to the master's *configuration*.

The obvious first reading of that model is one bounded context containing two
aggregates. It reads that way because the tables are joined, and because in the
business's own language an ELV exists "under" an LRV.

Two things make it the wrong reading.

**Different counterparties and different lifecycles.** A master leasing contract is
an agreement between the Lessor (*Leasinggeber*) and an Employer
(*Arbeitgeber*): a framework negotiated once, versioned as terms change, cancelled
with a notice period on a stated ground. An individual leasing contract is one
employee's lease of one bike: created from a sales order (*Antragsnummer*),
running a fixed term to a residual value, terminated early against a return quota.
The parties differ, the triggers differ, the states differ, and the words differ.

**The same word means different things.** *Kündigungsgrund* on a master contract is
a lessor's ground for terminating a framework agreement. On an individual contract
it is the reason one lease ended — employee left, bike written off, quota claimed.
A shared `CancellationReason` type would force one vocabulary onto the other, which
is the specific failure bounded contexts exist to prevent.

The counter-argument, and it is a real one: term inheritance. An individual
contract's configuration inherits from the master's, and `inheritance_mode` even
allows a live link. That is genuine coupling and a context boundary makes it more
expensive to express.

## Decision

**Two bounded contexts**, at
`com.jobradleasing.contractmanagement.masterleasing` and
`com.jobradleasing.contractmanagement.individualleasing`.

**`EmployerId`, `LessorId` and `Money` live in `shared.domain`**, because both
contexts reference them and neither owns them (ADR 0005 categories 3 and the
`Money` argument in `architecture.definition.md` § 9).

**`MasterLeasingContractId` stays in `masterleasing`.** Where `individualleasing`
references it, it is a plain `String` (ADR 0005 category 2).

**Term inheritance crosses the boundary through `masterleasing`'s published
inport**, not through a shared type: `ReadLeasingTermsUseCase` (UC09) returns the
commercial terms a lease needs, and `individualleasing` copies the ones it uses
onto its own configuration.

The resulting package structure:

```
com.jobradleasing.contractmanagement
├── bootstrap
│   ├── ContractManagementApplication
│   ├── SharedConfig
│   ├── MasterLeasingConfig
│   └── IndividualLeasingConfig
├── shared
│   ├── domain
│   │   ├── EmployerId, LessorId, Money, Percentage
│   │   └── event
│   │       ├── DomainEvent
│   │       └── MasterLeasingContractActivated
│   ├── outport                     ← ClockPort, DomainEventPublisher
│   └── outbound
├── masterleasing                   ← MasterLeasingContract + MlcConfiguration
│   └── core / inbound / outbound
└── individualleasing               ← IndividualLeasingContract + IlcConfiguration
    └── core / inbound / outbound
```

## Rationale

### Why a separate bounded context rather than two aggregates in one

Bounded contexts exist to keep models consistent and independently evolvable. The
*Kündigungsgrund* collision above is the concrete test: in one context the two
meanings would have to share a name or be distinguished by prefix
(`MasterCancellationReason`, `LeaseCancellationReason`), and a model that needs
prefixes to keep its words apart is two models in one package.

The lifecycle argument is the same test applied to states. A master contract is
`DRAFT → ACTIVE → CANCELLED | ENDED`; a lease is
`PENDING_ACTIVATION → ACTIVE → TERMINATED | EXPIRED`. Merging them would produce
one enum serving two machines, and every `when` over it would need to know which
kind of contract it was looking at.

### Why term inheritance does not defeat the argument

It is the strongest objection and it resolves in the boundary's favour, because
what crosses is **values, not model**.

A lease needs to know a credit limit, a price band, a contract type and an
activation date. It does not need to know what an `MlcConfiguration` *is*, how it
is versioned, or that `inheritance_mode` exists. `ReadLeasingTermsUseCase` returns
exactly the values, as a `LeasingTermsResult` of shared and primitive types, and
`individualleasing` never sees the master aggregate.

That is an anti-corruption boundary doing its job rather than a leak. If
`masterleasing` restructures how configurations are versioned, `individualleasing`
does not recompile.

The honest cost: a synchronous call on the issue path, and `individualleasing`
depending on `masterleasing`'s availability. That is recorded in
`architecture.definition.md` § 11 rule 3 and accepted — the alternative is a copy
of the terms that goes stale, which is a worse failure because it is silent.

### Why the identities split the way they do

`EmployerId` and `LessorId` are on both contracts, refer to records in systems
outside this one, and carry an identical invariant. Keeping them in `shared`
couples both contexts to the *external* contract rather than to each other, which
is what a shared kernel is for.

`MasterLeasingContractId` is different: `masterleasing` owns it. Promoting it to
`shared` so `individualleasing` could hold the typed version would mean
`masterleasing` cannot change its identity representation without recompiling the
other context — the coupling this split exists to avoid. It crosses as a `String`.
See ADR 0005.

### Why not a full anti-corruption layer with its own translation types

`LeasingTermsResult` *is* the translation type. Adding a second layer that maps it
to an `individualleasing`-owned type would be mapping a DTO to a DTO. The result
type is already owned by the publisher and contains no model.

### Why `Money` is shared although it is not an identity

Both contexts denominate amounts; the rounding, scale and currency-agreement rules
are identical; neither context owns the concept of money. Two copies would be two
implementations of the same rounding rule, and rounding rules that drift are how a
leasing system starts disagreeing with itself by cents. See
`architecture.definition.md` § 9.

## Consequences

- `masterleasing` has zero compile-time dependencies on `individualleasing`'s
  internals, and vice versa. Enforced by `ContextRegistryTest`.
- `individualleasing` depends on `masterleasing.core.inport` — its published API,
  and nothing else. Enforced by two further `ContextRegistryTest` rules, including
  that only a **driver** may make the call.
- `masterleasing` depends on `individualleasing.core.inport` for UC04's cancellation
  fan-out. The dependency is mutual at the inport level and closed everywhere else,
  which is a deliberate narrowing of the usual "contexts must not depend on each
  other" formulation — see `architecture.definition.md` § 11 rule 3.
- `CancellationReason` is declared twice, once per context, and that is correct
  rather than duplication.
- Two Flyway migration families, two repository ports, two exception hierarchies,
  two GraphQL schema files.
- Neither context can be deployed without the other today, because UC04 shares a
  transaction across them (`architecture.definition.md` § 10). If that ever needs to
  change, the interaction model change is an ADR (`sdd.playbook.md` § 6 item 10).
