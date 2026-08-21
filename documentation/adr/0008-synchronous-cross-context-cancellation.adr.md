# ADR 0008 – Synchronous Cross-Context Cancellation

## Status
Rejected

Rejected by the maintainer, on the grounds that it recorded a decision that did not need
making. Kept because the *rejection* is the useful part: it documents why the outport
approach was considered and why plain orchestration is preferred.

The rule this ADR would have established now lives in `architecture.definition.md` § 11
rule 3, as doctrine rather than as a decision record.

## Context

UC12 (CancelTourByGuide) must cancel the bookings attached to a tour, and UC12 § 6 requires
that a failure on the booking side roll back the tour cancellation — the guide must not be
told "cancelled" while participants still hold live bookings.

This ADR proposed reaching that outcome through a **new outport**:
`guide.core.outport.BookingCancellationPort`, declared by `guide` and implemented by an
adapter in `booking` which would in turn delegate to `booking`'s inport. It framed the
result as a new cross-context interaction model, and therefore as an ADR-level decision
under `sdd.playbook.md` § 6 items 5 and 10.

## Decision

**Rejected.** The use case implementation — the driver — orchestrates directly:
`CancelTourByGuideDriver` cancels the guide tour, then calls `booking`'s inport
synchronously within the same transaction.

No ADR is required for that, because it is not a new mechanism. It is what
`architecture.definition.md` § 4.4 already says a driver does: coordinate a use case,
own the transaction boundary, and call the ports and APIs the coordination needs.

## Rationale for the rejection

### The outport added indirection without reducing coupling

The proposed `BookingCancellationPort` would have had exactly one implementation, in
`booking`, delegating to `booking`'s own inport. `guide` would still depend on `booking`'s
behaviour, contract, availability and failure modes — the dependency would simply have been
routed through an extra interface so that it did not appear in the import graph.

That is coupling relocated, not coupling removed, and the appearance of decoupling is worse
than the honest version: a reader of the import graph would conclude the contexts are
independent when the runtime says otherwise.

### A context's inport *is* its published API

`architecture.definition.md` § 4.2 calls `core.inport` "the application boundary" and
requires it to be framework-free and stable — "small and intention-revealing", a contract.
That is the description of a published API. Depending on another module's published API is
ordinary composition; reaching into its domain, its outports or its adapters is not. Rule 3
now draws the line there instead of at the context edge.

### So the only real question was the rule, not the mechanism

The original rule 3 forbade any `guide` → `booking` import, so the design failed
`ContextRegistryTest` — verified empirically before this ADR was rejected. That made it look
like an architectural decision. It was not: it was a rule stated more broadly than it needed
to be, of the same kind as several others corrected on this branch
(`coding-style.definition.md` § 3.2's dead layering, § 5.1's `private final`, § 4.2's query
prefixes). The fix is to state the rule correctly, not to design around it.

### What is preserved from the proposal

The reasoning about *why* this case is synchronous while UC06 is event-driven was sound and
has been kept — it now lives in § 11 rule 3's table and in UC12 § 6:

- A failed activation is recoverable and invisible; nobody was told anything untrue.
- A failed cancellation is a lie already delivered — the guide sees success while
  participants hold bookings for a tour that is not happening.
- An outbox makes delivery reliable but not atomic, so the inconsistency window shrinks
  rather than closing; a saga with a compensating un-cancel is worse, since reversing a
  cancellation people may already have been notified about is a second wrong.

The accepted costs are likewise unchanged and recorded in UC12 § 6: temporal coupling for
this one operation, a transaction spanning two contexts, and longer lock duration while
there is still no optimistic locking.

## Consequences

- `architecture.definition.md` § 11 rule 3 is narrowed: a context may depend on another
  context's `core.inport`, and on nothing else of it. Three `ContextRegistryTest` rules
  enforce this, including that only a **driver** may make the cross-context call — a
  controller or listener doing it would scatter the coupling across the delivery surface.
- This narrows a claim in ADR-0003 ("both contexts depend on `shared.domain` only"). ADR-0003
  is immutable and stands as the record of the extraction; § 11 is the current rule.
- No `BookingCancellationPort` is introduced. UC09's and UC12's specs are updated to describe
  driver-to-inport orchestration.
- Rejected ADRs are kept, not deleted. The alternative considered and the reason it lost are
  more useful to a future reader than a clean numbering sequence.
