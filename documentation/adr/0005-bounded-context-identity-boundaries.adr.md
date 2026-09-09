# ADR 0005 – Bounded Context Identity Boundaries

## Status
Accepted — with its worked example gone.

`TourId` was the shared kernel's only identity type and was deleted with the tour example, so
the shared kernel currently holds **no** identity at all. The decision itself is unaffected:
a foreign context's identity still crosses as an opaque value, and this ADR's reservation —
adding a member to the shared kernel requires an ADR — is now unspent. `adr/0023` spent it
once, declining to promote three `mlc` types, and was withdrawn with them.

## Context

`modelling.definition.md` § Identity stated, without qualification:

> - IDs MUST be modeled as Value Objects.
> - IDs SHOULD NOT be primitive types.

`architecture.definition.md` § 10 reinforced it, forbidding "primitives for domain
concepts when they carry meaning (money, **ids**, email, etc.)".

Meanwhile ADR 0003, which extracted the `guide` bounded context, explicitly permitted
a "plain string correlation ID" for `guideTourId` where it crosses into `booking`. The
code follows ADR 0003: `TourBooking.markActive(Instant, String)`,
`BookingActivated.guideTourId`, `TourStarted.guideTourId`.

`ddd-hex-reviewer` surfaced this as a conflict rather than a violation, correctly
declining to rule on it: per the authority order in `CLAUDE.md`,
`modelling.definition.md` (rank 4) outranks `adr/` (rank 10), so ADR 0003 had
permitted something a higher-ranked document forbade. One of the two had to change.

The underlying question is not about `guideTourId`. It is: **when an identity crosses
a bounded context boundary, does the receiving context share the owner's type?**

The unconditional rule forces "yes", and that answer has a consequence that was never
examined. To share the type, it must be visible to both contexts, which means
promoting it into `shared`. Applied consistently, every identity referenced across a
boundary migrates to the shared kernel — and the shared kernel becomes the union of
both contexts' identity models.

## Decision

The Value Object rule for identities is **scoped to the owning bounded context**.
Three categories:

**1. Own identities — MUST be Value Objects.**
An identity owned by this context is a Value Object, immutable, validating its format
at construction, defined *within* the context (never in `shared`).
`BookingId`, `GuideTourId`.

**2. Foreign identities — MAY be plain `String`.**
A reference to an identity owned by *another* of our contexts may be carried as a
plain `String`, in commands, results, domain events and aggregate state alike. It is
opaque: no parsing, no format assumptions, no business meaning in the receiving
context, and no reconstructing the owner's Value Object from it.
`guideTourId` inside `booking`.

**3. Identities owned by no context in this system — MAY be a shared Value Object.**
An external system's identifier, referenced by more than one of our contexts and owned
by none of them, may live in `shared` as a Value Object.
`shared.domain.TourId`, and today nothing else.

Category 3 is deliberately narrow. "Both contexts use it" is not the test; the test is
whether **neither** context owns it. If one does, the other uses a `String`.

`modelling.definition.md` § Identity is amended to state this. ADR 0003's permission
is retroactively consistent — it was applying this policy before the policy was
written down.

## Rationale

### Why not simply promote foreign IDs into the shared kernel?

Because that trades context independence for type safety on a value the receiving
context never reasons about, and independence is the more valuable property.

If `booking` imports `GuideTourId`, then `guide` can no longer change how it
identifies tours without recompiling `booking`. The contexts co-evolve through the
shared kernel, which is precisely the coupling that motivated ADR 0003's split. Every
cross-boundary reference added over time widens `shared` further, until it holds the
union of both identity models and the "bounded" in bounded context stops meaning
anything.

The shared kernel should hold what is genuinely *neutral* between contexts, not
whatever two contexts happen to both mention.

### Why is `String` the honest type rather than a weaker one?

The receiving context does not own the identity and cannot enforce its invariants. It
cannot know whether a `guideTourId` is well-formed, whether it still exists, or what
its format will be next quarter — only `guide` knows that. A Value Object in
`booking` would *claim* validation authority it does not have, and its constructor
would either duplicate `guide`'s rules (drifting the moment `guide` changes them) or
validate nothing, which is worse than a `String` because it looks like a guarantee.

A `String` states the actual epistemic position: an opaque handle, received from
elsewhere, to be stored and echoed back.

### Why does the boundary count as a serialization boundary?

Because that is what it already is in every other respect. Cross-context communication
goes through `shared.domain.event` (ADR 0002) or an explicit outport — mechanisms that
serialize. The receiving side is a consumer of a published fact, not a co-owner of the
publisher's model. Identities crossing that boundary are payload, and payload is
primitive-typed by nature.

### Why keep `TourId` in `shared` rather than making it a `String` in both contexts?

`TourId` is not a foreign identity from a *peer context* — it identifies an entry in an
external tour catalogue. There is no `Tour` aggregate anywhere in this system, so
neither `booking` nor `guide` owns it, and keeping it shared couples both to the
external contract rather than to each other. The contexts remain mutually independent,
which is the property this ADR protects.

It also carries a real invariant both contexts need identically (non-blank), enforced
once at 22 call sites rather than duplicated or dropped.

### Why not enforce category 2 as MUST rather than MAY?

A synchronous outport that returns a typed result from the owning context is a
legitimate design (UC09/UC12's `BookingCancellationPort`), and there the owner's type
may legitimately appear in the signature. `MAY` leaves room for that without
sanctioning drift, because category 2's opacity constraints still apply wherever the
`String` form is chosen.

## Consequences

Positive:

- `shared` stays minimal. It holds `TourId`, `DomainEvent`, the cross-context events,
  `ClockPort` and `DomainEventPublisher` — and gains nothing merely because a second
  context references it.
- `guide` can change `GuideTourId`'s representation without touching `booking`.
- The existing code is correct as written; no migration.
- `ddd-hex-reviewer` gains a checkable rule: it can now distinguish an own-identity
  primitive (drift) from a foreign-identity `String` (correct), which the previous
  unconditional wording made impossible.

Negative / accepted trade-offs:

- Foreign identities lose compile-time type safety. Accepted: the receiving context
  does not validate them anyway, and category 2's opacity constraints are reviewable.
- Two identity styles coexist in one codebase, and which applies depends on ownership
  rather than on the type. Mitigated by the naming constraint (`guideTourId`, not `id`)
  and by ownership being explicit in `architecture.definition.md` § 11.
- Nothing mechanically prevents a `String` foreign id from being parsed or branched on.
  This is a review concern, and it is now written down for the reviewer to enforce.
- Category 3 is a judgement call, and judgement calls erode. The "neither context owns
  it" test is the guard; `TourId` is the only member, and adding a second should
  require an ADR.

## Future Considerations

- If a `Tour` aggregate is ever introduced in this system, `TourId` moves out of
  `shared` into its owning context and becomes a category-2 `String` everywhere else.
  That is a breaking change across ~22 files and warrants its own ADR.
- `architecture.definition.md` § 10's anti-pattern wording ("primitives for domain
  concepts … ids") is amended alongside this ADR to point at the scoped rule, so the
  two documents cannot drift apart again.
- If foreign-identity opacity proves hard to police by review, a marker type such as
  `ForeignId<T>` could make the intent explicit without importing the owner's type —
  but that is speculative, and a `String` is sufficient until it demonstrably is not.
