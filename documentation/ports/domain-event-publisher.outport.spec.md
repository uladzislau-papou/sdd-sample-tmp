# Port Specification – DomainEventPublisher (Outport)

## Purpose

Defines the boundary for publishing domain events after a use case completes.
This outport decouples the application layer from the event delivery mechanism.

SDD: See `documentation/adr/0002-domain-event-publication.adr.md` for the post-commit strategy.


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
void publish(DomainEvent event)
```

**Responsibility:** Hand off a domain event for delivery after the active transaction commits.

**Preconditions:**
- `event` must be non-null.
- Must be called within an active transaction (the implementation handles the post-commit timing).

**Postconditions:**
- The event is queued for delivery after the transaction commits successfully.
- If the transaction rolls back, the event MUST NOT be delivered.

**Exceptions:** No checked exceptions. Infrastructure failures in event delivery are logged and do not roll back the transaction.

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


## 3. Post-Commit Guarantee

As specified in ADR 0002, the driver calls this port within the `@Transactional` boundary.
The implementation uses Spring's `ApplicationEventPublisher` to publish the domain event as a Spring application event.
A `@TransactionalEventListener(phase = AFTER_COMMIT)` listener in the `listeners` package receives the event after commit.

This guarantees:
- Events are not delivered if the transaction rolls back.
- The domain layer has no awareness of the delivery mechanism.


## 4. Reference Implementation

Class: `outbound.integration.LoggingDomainEventPublisher`

Behaviour: Calls Spring `ApplicationEventPublisher.publishEvent(event)`. The listener logs the event via SLF4J.


## 5. Constraints

- The interface MUST remain framework-free.
- MUST NOT be called outside of a transaction boundary.
- MUST NOT attempt synchronous external delivery (no HTTP calls, no broker writes inside this method).
- Domain events MUST NOT be leaked as integration events without explicit mapping.