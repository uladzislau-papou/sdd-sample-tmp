# ADR 0005 – Bounded Context Identity Boundaries

## Status
Accepted

## Context

`modelling.definition.md` § Identity and `architecture.definition.md` § 10 together
forbid primitives for domain concepts, ids included. Stated without qualification,
that rule says: every identity is a Value Object.

Applied to this domain, the unqualified rule produces an immediate problem and a
less obvious one.

**The immediate problem.** `INDIVIDUAL_LEASING_CONTRACT.mlc_id` references a master
contract. If it must be a Value Object, `individualleasing` needs
`MasterLeasingContractId`, which means the type has to be visible to both contexts,
which means promoting it into `shared`. Applied consistently, every identity
referenced across a boundary migrates to the shared kernel — and the shared kernel
becomes the union of both contexts' identity models, which is the coupling ADR 0003
split them to avoid.

**The less obvious problem.** This domain also has identities belonging to systems
*outside* this one: `job_cyclist_id` (the employee), `bike_id` (the *Leasingobjekt*),
`kau_number` (the *Antragsnummer*), `partner_number` (the Odoo ↔ Radar join key).
Some are referenced by both contexts, some by exactly one. The unqualified rule says
"Value Object" for all of them and says nothing about *where*, and the obvious
default — "it is foreign, so it is a `String`" — would strip the type from fields
that have a genuine invariant and no boundary to protect.

The underlying question is: **when an identity crosses a boundary, or comes from
outside, does it keep its type — and if so, whose?**

## Decision

The Value Object rule for identities is **scoped by ownership**. Four categories.

**1. Own identities — MUST be Value Objects, inside the owning context.**
An identity owned by this context is a Value Object, immutable, validating its
format at construction, defined *within* the context (never in `shared`).

`MasterLeasingContractId`, `IndividualLeasingContractId`, `ElvNumber`.

**2. Foreign identities from a peer context — MAY be plain `String`.**
A reference to an identity owned by *another of our contexts* may be carried as a
plain `String`, in commands, results, domain events and aggregate state alike. It is
opaque: no parsing, no format assumptions, no business meaning in the receiving
context, and no reconstructing the owner's Value Object from it.

`masterLeasingContractId` inside `individualleasing`.

**3. Identities owned by no context here, referenced by more than one — MAY be a
shared Value Object.**
An external system's identifier, referenced by more than one of our contexts and
owned by none of them, may live in `shared.domain` as a Value Object.

`EmployerId`, `LessorId`.

**4. Identities owned by no context here, referenced by exactly one — Value Object,
inside that context.**
An external identifier only one of our contexts references is a Value Object in that
context. Not shared, because nothing else uses it; not a `String`, because category
2's reasoning does not apply.

`JobCyclistId`, `BikeId`, `KauNumber` in `individualleasing`; `PartnerNumber` in
`masterleasing`.

`modelling.definition.md` § Identity states this and carries the decided table.

## Rationale

### Why not simply promote foreign IDs into the shared kernel (against category 2)

Because that trades context independence for type safety on a value the receiving
context never reasons about, and independence is the more valuable property.

If `individualleasing` imports `MasterLeasingContractId`, then `masterleasing` can no
longer change how it identifies contracts without recompiling the other context. The
contexts co-evolve through the shared kernel, which is precisely the coupling that
motivated ADR 0003's split. Every cross-boundary reference added over time widens
`shared` further, until it holds the union of both identity models and the "bounded"
in bounded context stops meaning anything.

The shared kernel should hold what is genuinely *neutral* between contexts, not
whatever two contexts happen to both mention.

### Why `String` is the honest type for category 2 rather than a weaker one

The receiving context does not own the identity and cannot enforce its invariants. It
cannot know whether a `masterLeasingContractId` is well-formed, whether the contract
still exists, or what the format will be next quarter — only `masterleasing` knows
that. A Value Object in `individualleasing` would *claim* validation authority it does
not have, and its constructor would either duplicate the owner's rules (drifting the
moment they change) or validate nothing, which is worse than a `String` because it
looks like a guarantee.

A `String` states the actual epistemic position: an opaque handle, received from
elsewhere, to be stored and echoed back through the owner's inport.

### Why the boundary counts as a serialization boundary

Because that is what it already is in every other respect. Cross-context communication
goes through `shared.domain.event` (ADR 0002) or a synchronous inport call — mechanisms
that pass values, not models. The receiving side is a consumer of a published fact, not
a co-owner of the publisher's model. Identities crossing that boundary are payload, and
payload is primitive-typed by nature.

### Why category 4 exists at all

This is the addition to the three-category rule the framework was inherited with, and
it exists because this domain has something the reference domain did not: external
identities used by exactly one context.

Under three categories those fields have no home. Category 1 requires ownership we do
not have. Category 2 requires a peer context whose independence the `String` protects —
there isn't one; the bike catalogue is not a bounded context of this system and is not
going to recompile. Category 3 requires two of our contexts to reference it.

Left unresolved, the default would have been a bare `String` for `bikeId` and
`jobCyclistId` "because they are foreign" — category 2's *conclusion* reached without
category 2's *reason*. And it would have been wrong on the merits: `KauNumber` and
`ElvNumber` have format invariants the business cares about, `PartnerNumber` is a join
key whose blankness is a data error worth catching at construction, and none of those
checks survive being a `String`.

So the discriminator is not "is it foreign" but **"is there a boundary the `String` is
protecting"**. For category 4 there is not, and
`architecture.definition.md` § 10's prohibition on primitives applies with full force.

### Why category 3 is deliberately narrow

`EmployerId` and `LessorId` qualify because both contexts hold them, both refer to
records in systems outside this one, and both carry an identical invariant enforced at
many call sites.

"Both contexts use it" is not the test; the test is whether **neither** context owns it.
If one does, the other uses a `String`. Category 3 is the one most likely to erode,
because every new shared-looking type will present itself as a member. Adding a third
member should require an ADR.

### Why category 2 is `MAY` rather than `MUST`

A synchronous inport call that returns a typed result from the owning context is a
legitimate design — `ReadLeasingTermsUseCase` (UC09) is exactly that — and there the
owner's result type legitimately appears in the caller's code. `MAY` leaves room for
that without sanctioning drift, because category 2's opacity constraints still apply
wherever the `String` form is chosen.

## Consequences

Positive:

- `shared` stays minimal: `EmployerId`, `LessorId`, `Money`, `Percentage`,
  `DomainEvent`, the cross-context events, `ClockPort`, `DomainEventPublisher` — and
  gains nothing merely because a second context references it.
- `masterleasing` can change `MasterLeasingContractId`'s representation without
  touching `individualleasing`.
- External identities keep their invariants instead of decaying to `String` by default.
- `ddd-hex-reviewer` gains a checkable rule: it can distinguish an own-identity
  primitive (drift) from a foreign-identity `String` (correct), which the previous
  unconditional wording made impossible.

Negative / accepted trade-offs:

- Category 2 identities lose compile-time type safety. Accepted: the receiving context
  does not validate them anyway, and the opacity constraints are reviewable.
- **Four categories is more rule than three, and the boundary between 2 and 4 is the
  one people will get wrong.** Both are "foreign"; only one has a peer context on the
  other side. `modelling.definition.md`'s decided table exists so the question is
  usually a lookup rather than a judgement.
- Nothing mechanically prevents a category 2 `String` from being parsed or branched
  on. This is a review concern, and it is now written down for the reviewer to enforce.
- Category 3 is a judgement call, and judgement calls erode. The "neither context owns
  it" test is the guard.

## Future Considerations

- If `SERVICE_AGREEMENT` (DLV) is ever built as a third context, `LessorId` is
  unaffected but `EmployerId` gains a third referent — still category 3.
- If a `Bike` or `Employee` aggregate is ever introduced *in this system*,
  `BikeId`/`JobCyclistId` move from category 4 to category 1, and every other context
  referencing them moves to category 2. That is a migration, and it is the reason the
  categories are stated in terms of ownership rather than of current placement.
