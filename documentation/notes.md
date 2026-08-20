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
pattern `pullDomainEvents().forEach(publisher::publish)` is now identical in all six
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

- ~~Documentation, especially in the domain directory, is missing the definitions for
  the guide bc~~ — closed. `documentation/domain/aggregate-guide-tour.spec.md`,
  `documentation/ports/guide-tour-repository.outport.spec.md` and
  `documentation/ports/start-tour.inport.spec.md` now exist. The guide domain spec
  records three enforcement gaps it found (G-01 to G-03) as open work.
