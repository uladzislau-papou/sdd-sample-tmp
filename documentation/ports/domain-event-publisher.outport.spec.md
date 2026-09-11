# Port Specification – DomainEventPublisher (Outport)

## Purpose

The outbound port through which drivers hand domain events off for **post-commit**
delivery.

SDD: See `adr/0002-domain-event-publication.adr.md` and
`adr/0004-generic-domain-event-publisher-signature.adr.md`.


## 1. Interface

```
shared.outport.DomainEventPublisher
```

Lives in `shared.outport` because both bounded contexts publish events
(`architecture.definition.md` § 9, § 11).

Framework-free: no Spring type appears here. The `ApplicationEventPublisher`
integration is confined to the adapter.


## 2. Method Contract

### 2.1 publish

```kotlin
fun publish(event: DomainEvent)
```

**Responsibility:** Hand one domain event to the delivery mechanism for publication
after the current transaction commits.

**Preconditions:** the caller is inside a transaction. Publishing outside one is a
programming error: there is no commit to defer to, and the event would be delivered
immediately, asserting a fact that was never persisted.

**Postconditions:**
- The event is scheduled for delivery after the active transaction commits.
- **Nothing is delivered if the transaction rolls back.**
- The publisher does not inspect, validate, transform or persist the event.

**Exceptions:** none as part of the contract. A consumer's failure is the
consumer's, and does not propagate back — consumers run in their own
`REQUIRES_NEW` transaction (ADR 0002).

**Idempotency:** not provided. Publishing the same event twice delivers it twice.
Idempotency is the **consumer's** responsibility, which is why
`IndividualLeasingContract.activate` is an idempotent no-op when already `ACTIVE`
(that aggregate's spec § 4).

### 2.2 The signature is typed to the marker, deliberately

`publish(DomainEvent)` rather than one overload per event type. The full argument is
ADR 0004; the short version is that drivers publish by draining:

```kotlin
contract.pullDomainEvents().forEach(domainEventPublisher::publish)
```

`pullDomainEvents()` returns `List<DomainEvent>`, so a per-type API could not consume
it without a `when (event) { is X -> ... }` ladder in every driver — pushing the
event taxonomy into the application layer and growing by one branch in eight drivers
per new event.

`DomainEvent` is a marker with no members and is **not sealed** (ADR 0004 § Why
`DomainEvent` is not sealed). Sealing it would require every event from both contexts
to live in one package, contradicting `modelling.definition.md`'s placement rule.


## 3. The Drain Pattern

Every driver publishes the same way, and the uniformity is the point:

```kotlin
repository.update(contract)
contract.pullDomainEvents().forEach(domainEventPublisher::publish)
```

`pullDomainEvents()` returns a snapshot **and clears the aggregate's list**, so a
second call returns empty. That makes the drain a transfer of ownership rather than
a read, and it is what stops a driver that loads, mutates and mutates again from
publishing the first event twice.

**A driver that forgets to drain loses its events silently.** Nothing structural
prevents it — the aggregate cannot publish (that would put infrastructure in
`core.domain`) and the port cannot know it was never called. The use-case test
asserting the publication is the only thing that catches it, which is why
`test.definition.md` § 2.2 lists emitted events as mandatory coverage rather than as
a nice-to-have.


## 4. Reference Implementation

`shared.outbound.integration.LoggingDomainEventPublisher`, wired by
`bootstrap.SharedConfig`.

Behaviour: logs the event at DEBUG and delegates to Spring's
`ApplicationEventPublisher`. Consumers bind with
`@TransactionalEventListener(phase = AFTER_COMMIT)` plus
`@Transactional(propagation = REQUIRES_NEW)`.

`REQUIRES_NEW` on the consumer is not optional: an `AFTER_COMMIT` listener runs when
the publisher's transaction is already gone, so without it each repository call would
run in auto-commit, one statement at a time, and a fan-out that failed halfway would
leave half its work committed with nothing to indicate it (ADR 0002).

It lives in `shared.outbound` rather than inside a context for the same reason
`SystemClockPort` does (`architecture.definition.md` § 9).

**Logging must not include the event's payload verbatim.** Several events carry
monetary terms, and `technical.spec.md` names salary-sacrifice amounts as sensitive:
a `conversion_rate_per_month` in a log line discloses what someone earns net.


## 5. Test Usage

Replaced by a **recording stub** that appends to a list. Driver tests then assert on
the recorded events by type and payload.

This is the only reasonable shape: there is no mocking framework
(`technical.spec.md`, Testing), and asserting "the publisher was called" would be an
implementation-detail assertion anyway. What the tests assert is *which facts were
published*, which is behaviour.


## 6. Constraints

- The interface MUST remain framework-free.
- Aggregates MUST NOT hold a reference to it — they record events, the driver
  publishes them (ADR 0002).
- The implementation MUST NOT deliver before commit.
- The implementation MUST NOT swallow a delivery failure silently; log it.
- Adding a second function (a batch `publishAll`, a `publish(event, metadata)`) is a
  change to the port's contract. Prefer adding properties to `DomainEvent` over
  widening this function (ADR 0004, Future Considerations).


## 7. Known Gaps

- **Delivery is not guaranteed.** A JVM death between commit and the listener
  running loses the event with no record that it existed (ADR 0002, Known
  limitation). For UC06 that means a lease stays `PENDING_ACTIVATION` under an
  `ACTIVE` master contract, and nothing retries.

  This is survivable **only because of which fan-out uses events**. Cancellation
  (UC04) deliberately does not: it shares a transaction, precisely so that the
  dangerous state — a cancelled master contract with live leases — cannot be
  produced by a lost event (`architecture.definition.md` § 10).

  A transactional outbox is the fix and is deferred while every consumer is
  in-process. Adopting it does not change this signature: serialising a
  `DomainEvent` to an outbox row is an adapter concern.
- **No ordering guarantee between events** published in one drain. Nothing today
  depends on order; a consumer that did would be relying on an unspecified property.
