# Port Specification – DomainEventPublisher (Outport)

## Purpose

Defines the boundary for publishing domain events after a use case completes.
This outport decouples the application layer from the event delivery mechanism.

SDD: See `documentation/adr/0002-domain-event-publication.adr.md` for the post-commit strategy.


## 1. Interface

```
core.outport.DomainEventPublisher
```

## 2. Method Contract

### 2.1 publish

```
void publish(TourBookingRequested event)
```

**Responsibility:** Hand off a domain event for delivery after the active transaction commits.

**Preconditions:**
- `event` must be non-null.
- Must be called within an active transaction (the implementation handles the post-commit timing).

**Postconditions:**
- The event is queued for delivery after the transaction commits successfully.
- If the transaction rolls back, the event MUST NOT be delivered.

**Exceptions:** No checked exceptions. Infrastructure failures in event delivery are logged and do not roll back the transaction.

**Scope:** The method signature is specific to `TourBookingRequested` for UC01.
Additional overloads or a generic signature may be introduced in later use cases via ADR.


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