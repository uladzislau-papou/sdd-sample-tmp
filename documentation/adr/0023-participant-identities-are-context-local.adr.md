# ADR 0023 – Participant Identities Are Context-Local, Not Shared

## Status
**Withdrawn** — its subject no longer exists.

It declined to promote `EmployerId`, `LessorId` and `PartnerNumber` to `shared.domain`. All
three types were deleted with the `mlc` context.

The reservation it was answering is **not** withdrawn: `adr/0005` still requires an ADR before
a second identity joins the shared kernel, and that reservation is now unspent again — the
shared kernel currently holds no identity type at all.

## Context

`uc07-create-master-leasing-contract.spec.md` § 2 needs three identities on its input
contract: `employerId`, `lessorId` and `partnerNumber`.

All three are **external**. `adr/0017-contract-data-ownership-boundary.adr.md` puts the
employer, the lessor and the partner number in the "not ours" column: the contract is ours,
its participants are not, and they arrive through the anti-corruption layer.

`adr/0005-bounded-context-identity-boundaries.adr.md` sorts identities into three
categories, and these three land in **category 3** — "identities owned by no context in this
system" — which *may* be a shared Value Object in `shared.domain`.

May, not must. And ADR-0005's Consequences add a brake:

> `TourId` "is the only member, and adding a second should require an ADR".

So the question reaches an ADR three times over, and it cannot be settled inside a use-case
spec. That is why `uc07` § 10 carries a box demanding this document rather than a decision.

The question was also mis-framed once, in a way worth recording. `adr/0017`'s Decision says
a participant is "held as an opaque identity value, per `adr/0005`" — wording that reads as
**category 2** (a reference to another of *our* contexts, carried as a plain `String`) while
describing category 3. The two categories have different defaults, so the ambiguity had to
be resolved before any type could be written.

## Decision

`EmployerId`, `LessorId` and `PartnerNumber` are **Value Objects local to the `mlc`
bounded context**, defined in `mlc.core.domain.masterleasingcontract`.

They are **not** added to `shared.domain`. The shared kernel keeps exactly one identity
member, `TourId`, and that member leaves with the tour example.

Each validates its own format at construction and throws
`InvalidMasterLeasingContractException` — a domain exception, per
`coding-style.definition.md` § 6.2 clause one, because all three arrive from a command.

When `ilc` is created and genuinely needs one of these identities, promoting it to
`shared.domain` is a **new** ADR, and this one is the record of why it was not promoted
pre-emptively.

## Rationale

**ADR-0005's own test is not met yet.** Category 3 is written as "an external system's
identifier, referenced by **more than one of our contexts** and owned by none of them". The
sentence has two conditions, and the section is explicit that the second is not sufficient
on its own: *"'Both contexts use it' is not the test; the test is whether **neither** context
owns it."* — the point being that both conditions have to hold.

Today `ilc` does not exist. `adr/0015-two-contexts-by-contract-level.adr.md` decides that it
will, and `architecture.definition.md` § 11 rule 2 makes an unregistered package drift, so
the second condition is not merely unproven — it is currently false, and will stay false
until a registered `ilc` package references one of these three types. Promoting them now
would satisfy category 3 on an intention rather than on a fact.

**A shared kernel with one consumer is a shared kernel with an untested rule.**
`architecture.definition.md` § 9 forbids `shared` from depending on any bounded context, and
§ 11 rule 4 makes a context-specific type in `shared` drift *even though `shared` is
registered*. A type placed there for a single context is exactly that drift, and it is the
kind a reviewer has to notice, because no import crosses a boundary — the same invisibility
that let `ClockPort` and `DomainEventPublisher` sit in booking's wiring while both contexts
were claimed to depend only on `shared.domain` (`bootstrap/SharedConfig.kt` records that
incident).

**The move is cheap and the reverse is not.** Promoting a Value Object from a context to
`shared` is a package move plus an exception swap. Demoting one *back* means finding every
context that quietly started depending on it. ADR-0005 chose the narrow default for this
reason; deferring is the reversible direction.

**`shared.domain` would inherit an exception problem it has no answer for.**
`coding-style.definition.md` § 6.3 is explicit that a value object in `shared.domain`
*cannot* satisfy clause one — it has no domain exception available and must fall back to
`IllegalArgumentException`, mapped to 400 at every boundary. That exemption is described
there as a backstop and explicitly **not** a licence for a context-owned value object to
skip clause one. Placing three command-sourced identities in `shared` would take the
exemption by choice rather than by necessity, on the very first contract types this service
owns.

**Rejected: a plain `String` (category 2).** It is not available. Category 2 is scoped to "a
reference to an identity owned by **another of our contexts**", and no context of ours owns
an employer. Reading `adr/0017`'s "opaque identity value" as category 2 would use the
category's permission while failing its condition — which is the mis-framing named in the
Context above, not a route out of it.

## Consequences

- The `mlc` context owns three small Value Objects that a future `ilc` cannot import
  (§ 11 rule 3 closes a context's `core.domain`). If `ilc` needs them, it either carries its
  own or a new ADR promotes them. This duplication is accepted and is the cost of the
  narrow default.
- `shared.domain` stays at one identity, so the tour example's departure removes the whole
  package rather than leaving a mixed one behind. This was a side effect, not a goal, and it
  is the cleanest available evidence that the narrow default is the right one for now.
- The three types throw a `mlc` domain exception, so the § 6.3 fallback is not used and
  `BAD_REQUEST` classification comes from the domain exception rather than from the
  `IllegalArgumentException` backstop.
- `adr/0005`'s Consequences are satisfied without amending it: a second shared member was
  proposed, considered, and declined. Its brake worked as designed.
