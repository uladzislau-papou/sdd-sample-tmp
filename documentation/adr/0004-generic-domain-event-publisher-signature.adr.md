# ADR 0004 – Generic DomainEventPublisher Signature

## Status
Accepted

Recorded **retroactively**. The decision this ADR describes is already implemented;
see *Why this ADR is retroactive* below.

## Context

`DomainEventPublisher` is the outbound port through which use cases hand domain
events off for post-commit delivery (ADR 0002).

When it was introduced for UC01 it was deliberately narrow:

```java
void publish(TourBookingRequested event);
```

`documentation/ports/domain-event-publisher.outport.spec.md` § 2.1 recorded that
choice and attached an explicit condition to changing it:

> **Scope:** The method signature is specific to `TourBookingRequested` for UC01.
> Additional overloads or a generic signature may be introduced in later use cases
> **via ADR**.

The signature is now generic:

```java
void publish(DomainEvent event);
```

Six event types implement the `DomainEvent` marker today — `TourBookingRequested`,
`TourBookingConfirmed`, `TourBookingCancelled`, `ParticipantsChanged`,
`BookingActivated`, `TourStarted` — and UC07, UC11 and UC12 specify three more
(`BookingCompleted`, `TourCompleted`, `TourCancelledByGuide`).

Every driver publishes the same way, by draining the aggregate:

```java
booking.pullDomainEvents().forEach(domainEventPublisher::publish);
```

No ADR was written when the signature widened. Neither ADR 0002 nor ADR 0003
mentions it, so the condition the port spec attached to the change was never met.

## Decision

`DomainEventPublisher` exposes exactly one method, typed to the `DomainEvent`
marker interface:

```java
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
```

- The port stays in `shared.outport`, because both bounded contexts publish events
  (`architecture.definition.md` § 9, § 11).
- Per-event-type overloads are **not** added. A new domain event requires no change
  to this port.
- The port remains framework-free; the Spring `ApplicationEventPublisher`
  integration stays entirely inside `LoggingDomainEventPublisher`.
- `documentation/ports/domain-event-publisher.outport.spec.md` § 2.1 is updated to
  specify the generic signature and to cite this ADR instead of the "via ADR"
  condition it previously carried.

## Rationale

### Why generic rather than per-type overloads?

The narrow signature could not survive contact with a second event type. Drivers
publish by draining `pullDomainEvents()`, which returns `List<DomainEvent>` — the
aggregate decides what it emitted, and the driver forwards whatever it finds. A
per-type API cannot consume that list without either downcasting or an
`instanceof` ladder in every driver, both of which push knowledge of the event
taxonomy into the application layer where it does not belong.

With nine event types specified, the overload approach would mean nine methods on
a port whose entire responsibility is "hand this fact to the delivery mechanism" —
a responsibility that does not vary by event type.

### Why is the marker interface the right boundary?

`DomainEvent` carries no members. That is the point: the port's contract is about
*timing and delivery*, not payload. The publisher never inspects the event, so the
narrowest type it can accept is the widest type the domain defines. Anything more
specific would be the port claiming knowledge it does not use.

Type safety is not lost where it matters. Consumers are typed —
`@TransactionalEventListener` methods bind to concrete event classes — so the
untyped hop is confined to the one call that genuinely does not care.

### Why not the alternative: a generic method `<E extends DomainEvent> void publish(E)`?

It would add no capability. The implementation still erases to `DomainEvent`, and
no call site needs the type parameter — nothing returns a value or takes a second
argument whose type must agree. It is ceremony without benefit.

### Why accept this retroactively rather than revert the code?

The implementation is right, and reverting would break five drivers to satisfy a
scope note that was always provisional — the port spec itself anticipated the
generic signature and merely required it be recorded. The defect is procedural,
not technical: a documented gate was bypassed. The correct remedy for a bypassed
recording requirement is to make the record, not to undo a correct decision.

## Consequences

Positive:

- Adding a domain event touches the aggregate, the event type and its consumers —
  never this port. UC07, UC11 and UC12 add three events with no change here.
- The port has one reason to change (delivery semantics), not one per event type.
- The `booking`/`guide` split needs no per-context publisher.

Negative / accepted trade-offs:

- The `publish` call site is not type-checked against a specific event. Accepted:
  the publisher does not read the event, and consumers are typed.
- `DomainEvent` becomes load-bearing. Any type implementing it is publishable, so
  the marker must not be applied to non-events. `architecture.definition.md` § 9
  already constrains what may live in `shared.domain.event`.
- A malformed event fails at delivery rather than at compile time. Mitigated by
  events being immutable records validated at construction
  (`modelling.definition.md`, Always-Valid).

## Why this ADR is retroactive

Recorded on 2026-08-20, after the fact, during the reconciliation pass that
introduced `tdd.definition.md`, `loop.playbook.md` and the `spec-documenter` /
`ddd-hex-reviewer` subagents.

The divergence was found by `spec-documenter`, which detected that
`domain-event-publisher.outport.spec.md` § 2.1 contracted
`publish(TourBookingRequested)` while `shared/outport/DomainEventPublisher.java:27`
declared `publish(DomainEvent)`. Per its Conflicts protocol it refused to edit
either side and escalated, rather than silently updating the spec to match the
code — which would have laundered the bypassed gate into a requirement.

This is the failure mode the review agents exist to catch: not wrong code, but a
correct decision that was never recorded, leaving the spec authoritative and wrong.
ADRs are immutable once accepted, so 0002 and 0003 are not amended; this ADR
supplies the missing record.

## Future Considerations

- **Delivery guarantees.** ADR 0002 defers the Transactional Outbox pattern. If it
  is adopted, this signature is unaffected — serialising a `DomainEvent` to an
  outbox row is an adapter concern.
- **Event metadata.** Should events need envelope data (occurredAt, correlation id,
  causation id), prefer adding it to `DomainEvent` as accessors over widening this
  method. That keeps the port at one method and makes the metadata available to
  every consumer. Note that `TourStarted` and `BookingActivated` currently carry
  `guideTourId` as a bare `String`, which `modelling.definition.md` disallows for
  identities — see the open question raised by `ddd-hex-reviewer` about ADR 0003's
  "plain string correlation ID" permission conflicting with the higher-ranked
  modelling doctrine.
