# ADR 0022 – The Outbox Row Is Written Inside the Caller's Transaction

## Status
**Withdrawn** — it decided a detail of `adr/0019`, which is itself withdrawn.

Worth reading anyway, for the reason recorded in its Context rather than its Decision: it
exists because ADR-0019 was written against `adr/README.md`'s one-line *summary* of ADR-0002
instead of against ADR-0002 itself, and the summary had collapsed two different facts into one
clause. That failure mode outlived the outbox.

## Context

`adr/0019-outbound-synchronisation-through-an-outbox.adr.md` requires a use case that changes
a contract to write its own state **and an outbox record in the same transaction**. In the
same document it describes the appender as "an adapter subscribed to [the domain event]" and
cites `adr/0002-domain-event-publication.adr.md` for publication "after commit".

Reviewing UC07 against both ADRs showed those cannot hold together. ADR-0002's reference
adapter delivers to listeners annotated
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`, so an appender
subscribed that way runs **after** the transaction has committed. Its row would be written in
a separate transaction, or not at all if the process dies in between — which is exactly the
atomicity the outbox exists to provide. An outbox whose row is not written atomically with the
state change is not an outbox; it is a second, worse notification channel.

The defect is in ADR-0019's reasoning, not in a disagreement between two positions. Two facts
make that clear:

1. **ADR-0002 anticipated this exact substitution.** Its Consequences say "Replacing this with
   an outbox or broker in production requires only a new `DomainEventPublisher` implementation
   and potentially a new listener. No driver or domain changes are needed", and its Future
   Considerations name it: "introduce `OutboxEntry` table and a scheduled relay process".
2. **ADR-0002's decision is about where publication is *called*, not when delivery happens.**
   Verbatim: "The driver publishes domain events within the `@Transactional` boundary via the
   `DomainEventPublisher` outport." That sentence is compatible with an outbox and always was.

What misled the author of ADR-0019 was `adr/README.md`'s one-line summary of ADR-0002 —
"Domain events are published after commit" — which collapses the call site and the delivery
moment into one clause. That line has been corrected. The lesson is recorded below.

## Decision

**The outbox row is written by the `DomainEventPublisher` adapter, synchronously, inside the
transaction that is already open.**

- The driver keeps doing what ADR-0002 decided: it pulls the aggregate's domain events and
  calls `domainEventPublisher.publish(event)` inside its `@Transactional` method. **No driver
  and no domain change.**
- The adapter implementing that port persists an outbox row using the ambient transaction, so
  the row and the aggregate commit or roll back together. It performs **no** outbound call.
- A **separate scheduled relay** reads pending rows, calls the foreign system, and records the
  outcome. It runs in its own transaction and knows nothing about the use case that produced
  the row.
- No `@TransactionalEventListener` participates in writing the row. Spring's
  `ApplicationEventPublisher` may still be used for in-process concerns that are genuinely
  allowed to be lost; it is not the sync path.

`adr/0002`'s **status stays `Accepted`**, and this ADR supersedes none of it. What it decided
is untouched. What no longer describes this service is its *reference adapter* — the Spring
`ApplicationEventPublisher` with an `AFTER_COMMIT` listener — and the consequence that
followed from that adapter: "This strategy provides in-process, at-most-once delivery. If the
JVM crashes after commit but before the listener fires, events are lost." Delivery here is
at-least-once, per `adr/0019`, and it survives a crash because the row is already committed.

## Rationale

**Why not keep the `AFTER_COMMIT` listener and accept the gap.** Because the gap is the whole
subject. Radar and Odoo are the leading systems (`adr/0017`); a contract change that reaches
us and never reaches them produces a divergence that nobody detects until somebody reads two
screens side by side. That is the failure mode `adr/0019` was written to prevent, and
accepting it would leave the outbox as ceremony.

**Why not write the row in `BEFORE_COMMIT` instead.** `@TransactionalEventListener` does offer
a `BEFORE_COMMIT` phase, and it would technically be inside the transaction. It is rejected
because it makes correctness depend on Spring's synchronization ordering and on a phase
constant that reads almost identically to the wrong one — a one-word edit turns a correct
outbox into a silently broken one, and no test that passes today would fail. A synchronous
call in the adapter has no such cliff.

**Why the adapter and not the driver writing the row directly.** Two reasons. The driver would
then know that an outbox exists, which makes every use case's transaction script carry an
infrastructure concern; and ADR-0002 already put the seam at `DomainEventPublisher` precisely
so this substitution costs nothing above it. Using the seam that was built for this is cheaper
than adding a second one beside it.

**What this costs.** The adapter now depends on persistence, which the previous adapter did
not. That is legitimate for an adapter (`adr/0011` bans persistence types from the *core*, not
from `outbound`), but it means the port's implementation is no longer trivially fake-able as a
no-op — a use-case test must either accept a fake publisher or run inside a transaction. UC07's
AC‑04 is written against a fake, which is the intended shape.

## Consequences

- One table and one scheduled relay, plus operational visibility on both: a row that keeps
  failing must stay visible rather than disappear into a log (`adr/0019`).
- The relay is the only component that knows Radar and Odoo exist. Its field mapping is still
  an open project question, recorded in `notes.md`.
- A use case's specification must name the events it emits, because those events are now
  literally the sync's input (`adr/0019`). UC07 names one.
- **A summary is not a source.** The defect this ADR corrects entered through a one-line
  restatement of an ADR in an index, which is the same class of failure as the three lists
  `CLAUDE.md` single-sources and as the stale test citations that produced `SpecCitationsTest`.
  The index's summaries now describe decisions in the ADR's own terms; where a one-liner cannot
  do that honestly, it should cite rather than paraphrase.
