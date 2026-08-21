---
type: note
goal: Humans take notes and share them
---

# Notes

Non-authoritative scratchpad. May not contradict formal definitions
(`file-usage.definition.md`).

## DomainEvent List inside AggregateRoot
Thinking about the DomainEvents. This is pretty neat when keeping the core clean.
On the other hand, its a little bit weird design. As long as we recall it weird,
I think we are fine.

Still open as a design musing. Worth noting that ADR 0004 settled the *port* side
of this (`publish(DomainEvent)` rather than per-type overloads), and that the drain
pattern `pullDomainEvents().forEach(publisher::publish)` is now identical in all eight
drivers — so the weirdness is at least uniform.

## Missing ReadModel
The reference implementation would benefit a read model. Attendees can then
see how ReadModels and Queries are placed.

Confirmed by `spec-documenter`: only `outbound/persistence/write` exists, and
`architecture.definition.md` § 4.6 documents an `outbound.persistence.read` side
that has no implementation. A read-side use case would exercise it — currently every
use case is a command, so the CQRS half of the package ontology is unused.

## UC12's DELETE verb vs the UC08 verb ruling
UC12 § 9 still specifies `DELETE /api/v1/guide-tours/{guideTourId}` while its flow
(`guideTour.cancel(cancelledAt, reason)`) carries a reason — the exact combination the
maintainer ruled indefensible for UC08 (`DELETE` bodies are dropped by some clients and
intermediaries; cancellation is a state transition, not a removal). UC12 is unimplemented,
so this is a spec question, not drift: should UC12 § 9 become `POST .../cancel` before
`/loop-uc UC12` runs? Raised by `spec-documenter` during the UC08 reconciliation.

---

*Closed notes*

- ~~UC07 drops the guide-tour correlation id — deliberate?~~ — closed, **not** deliberate.
  `TourCompleted` carries `guideTourId` and UC06 threaded it all the way through
  (`MarkBookingActiveCommand` → `BookingActivated`), but the first UC07 implementation
  discarded it at every hop while UC07 § 2 still listed it as an input. Reported by
  `spec-documenter`. Resolved by propagating it rather than by amending the spec: half a
  correlation trail is worse than none, because it looks complete. `BookingCompleted`,
  `MarkBookingCompletedCommand` and `TourBooking.markCompleted` now all carry it, and
  each of the three hops has its own test, mutation-verified.
- ~~Documentation, especially in the domain directory, is missing the definitions for
  the guide bc~~ — closed. `documentation/domain/aggregate-guide-tour.spec.md`,
  `documentation/ports/guide-tour-repository.outport.spec.md` and
  `documentation/ports/start-tour.inport.spec.md` now exist. The guide domain spec
  records three enforcement gaps it found (G-01 to G-03) as open work.
