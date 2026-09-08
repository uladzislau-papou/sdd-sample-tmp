# Port Specification – DomainEventPublisher (Outport)

## Purpose

Defines the boundary for handing domain events off **from inside the caller's transaction**.
Delivery timing is not part of this port's contract — it belongs to the adapter (§ 3).

> This paragraph said "publishing domain events **after a use case completes**" until
> `ddd-hex-reviewer` found it on the round *after* the one that reconciled § 2 through § 5. The
> file being fixed still contradicted itself in the first sentence a reader meets.
This outport decouples the application layer from the event delivery mechanism.

SDD: See `documentation/adr/0002-domain-event-publication.adr.md` for the **call site** — the
driver publishes inside its `@Transactional` boundary — and
`documentation/adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md` for
**delivery timing**. Collapsing those two into one clause is the defect § 3 documents.


## 1. Interface

```
shared.outport.DomainEventPublisher
```

Lives in `shared.outport`, not a context's `core.outport`: both `booking` and
`guide` need it, so it is shared-kernel infrastructure
(`architecture.definition.md` § 9, § 11). Moved there during the guide-context
extraction (`adr/0003-separate-guide-bounded-context.adr.md`).

## 2. Method Contract

### 2.1 publish

```
fun publish(event: DomainEvent)
```

**Responsibility:** Hand off a domain event for delivery. **Delivery timing is not part of this
port's contract** — see § 3.

**Preconditions:**
- Must be called **inside the caller's active transaction**. This is the whole of the port's
  contract, and `adr/0002` is what places the call there: the driver publishes within its
  `@Transactional` boundary.
- `event` non-null is expressed by the type; Kotlin needs no precondition for it.

**Postconditions:**
- The event has been handed to the adapter. What the adapter then does — deliver after commit,
  write an outbox row, both — is the adapter's property and varies by adapter.

**Exceptions:** No checked exceptions. Whether an infrastructure failure rolls back the caller's
transaction now depends on the adapter: `adr/0022`'s outbox adapter writes inside the ambient
transaction, so a failure there **does** roll the caller back, which is the point of it.

> **The signature line above said `void publish(DomainEvent event)` until this increment** —
> Java, months after the Kotlin migration. Found by `ddd-hex-reviewer` as a pre-existing item
> while it was checking the § 3 contradiction below.

**Scope:** The signature is generic — typed to the `DomainEvent` marker interface,
not to any concrete event type. Per-event-type overloads are **not** added; a new
domain event requires no change to this port.

Decided in `adr/0004-generic-domain-event-publisher-signature.adr.md`.

This section previously specified `void publish(TourBookingRequested event)`,
specific to UC01, and stated that a generic signature "may be introduced in later
use cases **via ADR**". The code widened to `publish(DomainEvent)` without that
ADR being written — a bypassed recording gate, found by `spec-documenter` and
escalated under its Conflicts protocol rather than being silently reconciled.
ADR 0004 supplies the missing record and accepts the implementation as correct.


## 3. Delivery Timing Belongs to the Adapter

**This section said the opposite until this increment, and the divergence was caused by this
increment.** It was titled *"Post-Commit Guarantee"* and specified that the event "is queued
for delivery after the transaction commits" and "MUST NOT be delivered if the transaction rolls
back". At `HEAD` that agreed with the port's KDoc, which said the implementation "is
responsible for delivering the event after commit (ADR 0002)". UC07 rewrote the KDoc and left
this section alone — so the one mechanical code→spec link, the `SDD:` line
(`coding-style.definition.md` § 7.3), pointed at a contradiction. `ddd-hex-reviewer` found it.

### What the port guarantees

Exactly one thing: **the caller publishes inside its own transaction.** That is `adr/0002`'s
decision, and it has not changed.

### What the port does not guarantee

*When* delivery happens. `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md`
makes this explicit: delivery timing is a property of the adapter behind the port, not of the
port. The two adapters that matter here differ:

| Adapter | Behaviour |
|---|---|
| `LoggingDomainEventPublisher` — **wired today** | Delegates to Spring's `ApplicationEventPublisher`. A `@TransactionalEventListener(AFTER_COMMIT)` therefore receives the event only after commit, and nothing is delivered on rollback. This is `adr/0002`'s reference adapter |
| The outbox adapter — **`adr/0022`, not built** | Writes an outbox row **synchronously in the ambient transaction** and performs no outbound call. A separate scheduled relay dispatches. The row commits with the aggregate or not at all |

So the post-commit guarantee the old § 3 described is real *today*, and it is exactly what
`adr/0022` replaces — because a listener subscribed `AFTER_COMMIT` writes its row after the
commit and loses the atomicity an outbox exists for.

### Why this distinction is the one that already caused a defect

`adr/0019` specified an outbox while citing `adr/0002` for "after-commit publication". Those
cannot both hold. The defect entered through `adr/README.md`'s one-line summary of `adr/0002`
— *"Domain events are published after commit"* — which collapses **where publication is
called** and **when delivery happens** into one clause. `adr/0002`'s actual decision was always
compatible with an outbox; its own Future Considerations name the substitution.

`adr/0002`'s status is therefore unchanged and correct at `Accepted`. Its *reference adapter*
was the thing in conflict, never its decision. `HANDOFF.md` § 5 records the lesson as **a
summary is not a source**, and this section is the third artifact the same collapse has
damaged.

### What swapping the adapter owes its existing callers

`TourStartedListener` is `@TransactionalEventListener(AFTER_COMMIT)` with
`REQUIRES_NEW`, so UC05 → UC06's fan-out **genuinely depends on post-commit delivery** — by
design, so that a failure in the fan-out cannot roll back a tour that really started.

That dependency is on the adapter in the first row above, not on this port. So the outbox
increment does not merely add an adapter: **it owes UC06 a migration note**, because an adapter
that writes a row in-transaction and dispatches from a relay changes when — and in which
transaction — that listener runs. Written here rather than left implicit, because the increment
that swaps the adapter will be reading this file and not `uc06`.

### Not the domain's concern either way

The domain layer has no awareness of the delivery mechanism under any adapter. `DomainEvent` is
a marker interface in `shared.domain.event`; the aggregate records events and
`pullDomainEvents()` hands them off.

## 4. Reference Implementation

Class: `shared.outbound.integration.LoggingDomainEventPublisher`, wired by
`bootstrap.SharedConfig`.

Behaviour: Calls Spring `ApplicationEventPublisher.publishEvent(event)`. **The adapter itself
logs nothing** — the *listener* logs the event via SLF4J, which is where the class's name comes
from and why the name misleads. UC07's first attempt at correcting the port KDoc asserted that
this class "logs", inferred from the name rather than read from the code.

It is `adr/0002`'s reference adapter and the one `adr/0022` replaces (§ 3).

It previously lived in `booking.outbound.integration` and was wired by `BookingConfig`,
so the `guide` context published its events through `booking`'s configuration
(`architecture.definition.md` § 9). Spring is permitted here: `shared.outbound` is an
adapter package, and only `shared.domain` and `shared.outport` are framework-free.


## 5. Constraints

- The interface MUST remain framework-free.
- MUST NOT be called outside of a transaction boundary. Under `adr/0022`'s adapter this stops
  being a stylistic rule and becomes the thing the atomicity rests on.
- MUST NOT attempt synchronous **external** delivery — no HTTP calls, no broker writes inside
  this method. An **outbox row** is not external delivery: it is a write to our own database,
  and `adr/0022` requires the adapter to make it synchronously inside the caller's transaction.
  The distinction is the whole design — the row is local and transactional, the dispatch is
  remote and deferred to a scheduled relay.
- Domain events MUST NOT be leaked as integration events without explicit mapping.