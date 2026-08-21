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
