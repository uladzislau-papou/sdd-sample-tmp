# ADR 0008 – Synchronous Cross-Context Cancellation

## Status
Proposed

Awaiting confirmation. `sdd.playbook.md` § 6 items 5 (modifying transaction boundaries)
and 10 (changing the cross-context interaction model) both fire. Implementation of UC09
and UC12 waits, per `execution.playbook.md` § 3.2.4.

## Context

Two contexts already communicate, and they do it one way: `guide` publishes a domain event
and `booking` reacts after the guide transaction commits (ADR-0002). UC05 → UC06 is exactly
that shape — `TourStarted` fires, `TourStartedListener` activates the bookings in a new
transaction, and if that fails the guide tour is already started and unaffected.

UC12 (CancelTourByGuide) and UC09 (MarkBookingCancelledByGuide) do not fit that shape. When
a guide calls off a tour, the bookings attached to it must be cancelled too, and a guide who
is told "tour cancelled" while participants still hold live bookings has been told something
false. The two facts have to agree.

Concretely, UC12 § 6 requires the port to be called **inside** the guide's transaction so
that a failure on the booking side rolls back the tour cancellation, and UC12 AC-07 makes
that a testable criterion: booking side fails → tour status unchanged, no event published,
HTTP 502.

That is a different interaction model from ADR-0002, and it modifies a transaction boundary
to span two bounded contexts. Both need recording.

## Decision

`guide` cancels bookings through a **synchronous outbound port, called inside its own
transaction**.

- **`guide` owns the abstraction.** `guide.core.outport.BookingCancellationPort` — the core
  declaring what it needs from outside (`architecture.definition.md` § 4.3). Framework-free,
  domain types only.
- **`booking` provides the implementation**, as an adapter satisfying that port, reached
  through `booking`'s own inport (`MarkBookingCancelledByGuideUseCase`, UC09). Neither
  context imports the other; the port is the seam.
- **One transaction spans both.** `CancelTourByGuideDriver` is `@Transactional`; the port
  call joins that transaction. A failure anywhere rolls the whole thing back.
- **`TourCancelledByGuide` is still published post-commit**, per ADR-0002. The synchronous
  call is about *consistency*; the event is about *notification*, and those stay separate.
- **Foreign identities cross as `String`**, per ADR-0005: the port carries `tourId` and
  `guideTourId` as opaque values, not as `booking`'s or `guide`'s value objects.

Event-driven cancellation is explicitly **rejected** — see below.

## Rationale

### Why not event-driven, like UC06?

Because the two cases differ in what a failure means.

For activation, a failure is recoverable and invisible: the tour is running, the booking is
still CONFIRMED, and a retry or a later reconciliation fixes it. Nobody was told anything
untrue.

For cancellation, a failure is a **lie already delivered**. The guide gets 200, the tour
reads CANCELLED, and participants hold bookings for a tour that is not happening. The
system has published two contradictory facts and there is no actor whose job it is to
notice. Eventual consistency is a fine default; it is the wrong default when the window of
inconsistency is a window of misinformation.

### Why not an outbox, or a saga?

Both would preserve the one-transaction-per-context rule and both are more machinery than
this earns.

An outbox makes delivery reliable, not *atomic* — the tour is still cancelled before the
bookings are, so the inconsistency window shrinks rather than closing. A saga with a
compensating "un-cancel" is worse: reversing a cancellation participants may already have
been notified about is not a compensation, it is a second wrong.

The synchronous call is the only option where the two facts are never in disagreement, and
both contexts share one database, so a shared transaction costs nothing infrastructurally
today.

### What this costs, stated plainly

`guide` becomes **temporally coupled** to `booking` for this one operation: if the booking
side is slow, `CancelTourByGuide` is slow; if it is down, `CancelTourByGuide` fails. That is
a real reduction in `guide`'s autonomy and it is the price of the guarantee above.

It is bounded, though. Exactly one operation is affected. Everything else stays
event-driven, and the port is the seam where a future split — different databases, a
network hop, an outbox with compensation — would be made.

### Why the guide side owns the port

Because the core owns the abstraction it depends on (§ 4.3). `guide` needs "cancel the
bookings for this tour" and should state that need in its own vocabulary. Putting the
interface in `booking` would make `guide` depend on `booking`'s naming, and § 11 rule 3
forbids the import that implies.

### Why 502 rather than 500 for a booking-side failure

The failure is in a collaborator the caller cannot influence — the same reasoning that maps
`AvailabilityUnavailableException` to 502 in UC01. A 500 would suggest the guide context
itself is broken.

## Consequences

Positive:

- The two facts cannot disagree. UC12 AC-07 is testable rather than aspirational.
- One clearly marked exception to the event-driven default, rather than a general licence.
- The port is the natural seam if the contexts are ever separated.

Negative / accepted trade-offs:

- **Temporal coupling** for this one operation, as above.
- **The transaction spans two contexts**, which the one-aggregate-per-transaction guideline
  (`architecture.definition.md` § 10) discourages. Accepted for the same reason the UC06
  fan-out is: no invariant spans the aggregates, so this is scoping, not a modelling error.
- **Two interaction models now coexist.** A reader has to know why UC06 is event-driven and
  UC09 is not. This ADR is that explanation, and both use case specs point at it.
- `ddd-hex-reviewer` will see a synchronous cross-context call and should not flag it. The
  reviewer checklist and § 11 need a sentence permitting a port-mediated synchronous call,
  so the rule is written down rather than living in this ADR alone.
- **Lock duration grows.** The guide transaction now holds locks on the guide tour *and*
  every affected booking. With no optimistic locking anywhere
  (`tour-booking-repository.outport.spec.md` § 6), this raises the contention surface.

## Future Considerations

- **Optimistic locking is now more attractive.** A wider transaction touching more rows makes
  the absence of a version column matter more. Its own ADR (persistence strategy, § 6 item 4).
- **If the contexts are ever split** across databases or a network, this decision is the
  first thing to revisit: the port survives, the shared transaction does not, and the
  replacement is an outbox plus an explicit reconciliation process — not a saga with a
  compensating un-cancel.
- **`architecture.definition.md` § 11 rule 3** currently says contexts communicate "through
  `shared.domain.event` or an explicit outport". That already permits this, but it does not
  say a synchronous port call may share a transaction. Worth one sentence once this is
  accepted.
- UC09 depends on UC08's `CancelledBy` value object and the extended `cancel(...)` signature,
  so UC08 lands first. The maintainer has ruled that UC08 itself needs no ADR.
