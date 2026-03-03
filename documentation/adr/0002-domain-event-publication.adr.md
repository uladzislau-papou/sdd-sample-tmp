# ADR 0002 – Domain Event Publication Strategy

## Status

Accepted

## Context

`modelling.definition.md` states:

> Application layer is responsible for publishing events AFTER successful transaction commit.

The `RequestTourBookingDriver` (and all future drivers) must publish domain events, but only if the transaction commits successfully. Publishing inside a `@Transactional` method with a naive implementation risks:

1. Events being delivered even when the transaction rolls back.
2. Premature delivery before the database write is visible to other readers.

At the same time, the reference implementation has no external message broker (Kafka, RabbitMQ, etc.) and must stay self-contained. The Transactional Outbox pattern is intentionally out of scope for this reference project.

## Decision

**The driver publishes domain events within the `@Transactional` boundary via the `DomainEventPublisher` outport.**

The reference implementation of `DomainEventPublisher` delegates to Spring's `ApplicationEventPublisher`:

1. The driver collects domain events from the aggregate via `booking.pullDomainEvents()`.
2. The driver calls `domainEventPublisher.publish(event)` inside the `@Transactional` method.
3. `LoggingDomainEventPublisher` calls `applicationEventPublisher.publishEvent(event)`.
4. Spring's `ApplicationEventPublisher` queues the event for post-commit delivery because the listener is annotated with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
5. If the transaction commits, the listener fires and logs the event.
6. If the transaction rolls back, the listener does NOT fire.

The `listeners` package hosts all `@TransactionalEventListener` methods.

## Rationale

### Why not publish directly inside `@Transactional`?

Direct publication (e.g., calling a side-effect service inline) would fire even on rollback, violating the "after commit" requirement.

### Why Spring `ApplicationEventPublisher` + `@TransactionalEventListener`?

- Zero infrastructure overhead (no broker, no outbox table).
- Satisfies the post-commit guarantee via Spring's built-in synchronization.
- The `DomainEventPublisher` outport remains framework-free (the Spring dependency is isolated in the adapter).
- Easy to replace: a future adapter could write to an outbox table or a broker without changing the driver.

### Why not the Transactional Outbox pattern?

The Transactional Outbox pattern provides stronger at-least-once delivery guarantees across process restarts. This is a valid production concern but is out of scope for a reference implementation focused on DDD and Hexagonal Architecture concepts.

## Consequences

- Spring `ApplicationEventPublisher` is used inside `outbound.integration` (adapter layer). The domain and inport/outport interfaces remain framework-free.
- `TourBookingEventListener` in the `listeners` package is the single integration point between domain events and side-effect execution.
- This strategy provides in-process, at-most-once delivery. If the JVM crashes after commit but before the listener fires, events are lost. This is acceptable for the reference scope.
- Replacing this with an outbox or broker in production requires only a new `DomainEventPublisher` implementation and potentially a new listener. No driver or domain changes are needed.

## Future Considerations

- Outbox pattern: introduce `OutboxEntry` table and a scheduled relay process.
- Broker integration: replace `LoggingDomainEventPublisher` with a Kafka/RabbitMQ publisher adapter.
- Generic event type: extend `DomainEventPublisher` to handle all domain event types rather than per-event overloads.