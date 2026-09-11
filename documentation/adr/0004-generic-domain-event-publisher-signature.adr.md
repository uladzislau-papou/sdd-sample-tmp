# ADR 0004 – Generic DomainEventPublisher Signature

## Status
Accepted

## Context

`DomainEventPublisher` is the outbound port through which drivers hand domain
events off for post-commit delivery (ADR 0002).

Its signature is a real choice with two candidates, and the narrow one is
tempting because it is more type-safe on its face:

```kotlin
// Candidate A — per-event-type overloads
fun publish(event: MasterLeasingContractRegistered)
fun publish(event: MasterLeasingContractActivated)
// ... one per event type

// Candidate B — typed to the marker
fun publish(event: DomainEvent)
```

There are nine event types across the two contexts today
(`MasterLeasingContractRegistered`, `MasterLeasingContractActivated`,
`MlcConfigurationAmended`, `MasterLeasingContractCancelled`,
`IndividualLeasingContractIssued`, `IndividualLeasingContractActivated`,
`IndividualLeasingContractTerminatedByLessee`,
`IndividualLeasingContractTerminatedByMasterContract`, and any added later), and
no reason to expect that number to stop growing.

## Decision

`DomainEventPublisher` exposes exactly one function, typed to the `DomainEvent`
marker interface:

```kotlin
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
```

- The port lives in `shared.outport`, because both bounded contexts publish events
  (`architecture.definition.md` § 9, § 11).
- Per-event-type overloads are **not** added. A new domain event requires no change
  to this port.
- `DomainEvent` is **not** a sealed interface — see below.
- The port remains framework-free; the Spring `ApplicationEventPublisher`
  integration stays entirely inside `LoggingDomainEventPublisher`.

## Rationale

### Why generic rather than per-type overloads

The narrow signature cannot survive contact with the drain pattern. Drivers publish
by draining `pullDomainEvents()`, which returns `List<DomainEvent>` — the aggregate
decides what it emitted, and the driver forwards whatever it finds:

```kotlin
contract.pullDomainEvents().forEach(domainEventPublisher::publish)
```

A per-type API cannot consume that list without a `when (event) { is X -> ... }`
ladder in every driver, which pushes knowledge of the event taxonomy into the
application layer where it does not belong, and grows by one branch in eight
drivers every time an event is added.

With nine event types, the overload approach would mean nine functions on a port
whose entire responsibility is "hand this fact to the delivery mechanism" — a
responsibility that does not vary by event type.

### Why the marker interface is the right boundary

`DomainEvent` carries no members. That is the point: the port's contract is about
*timing and delivery*, not payload. The publisher never inspects the event, so the
narrowest type it can accept is the widest type the domain defines. Anything more
specific would be the port claiming knowledge it does not use.

Type safety is not lost where it matters. Consumers are typed —
`@TransactionalEventListener` binds to a concrete event class — so the untyped hop
is confined to the one call that genuinely does not care.

### Why `DomainEvent` is not sealed, although Kotlin makes it easy

This is the question the Kotlin version of this decision has to answer and the Java
version did not.

A `sealed interface DomainEvent` would give exhaustive `when` handling over every
event in the system, which is normally a benefit this project actively seeks
(`coding-style.definition.md` § 2.3). It is rejected for two reasons:

1. **Sealing requires all implementations in the same package and module.** Every
   event from both contexts would have to live in `shared.domain.event`, which
   directly contradicts `modelling.definition.md`: a context-local event belongs in
   `<context>.core.domain.<aggregate>.event`, and only cross-context events belong
   in `shared`. Sealing would promote eight context-local types into the shared
   kernel to satisfy a language feature.

2. **Nobody wants the exhaustiveness.** Exhaustive `when` is valuable where a piece
   of code must handle every case — a state machine, a command dispatcher. No code
   here switches over all events; consumers subscribe to the one they care about.
   An exhaustiveness guarantee nothing consumes is a constraint with no beneficiary.

The one place a sealed hierarchy *would* help — catching an event type that no
consumer handles — is not what sealing checks. It checks that a `when` is
exhaustive, not that a subscriber exists.

### Why not a generic function `<E : DomainEvent> publish(event: E)`

It would add no capability. The implementation still erases to `DomainEvent`, and
no call site needs the type parameter — nothing returns a value or takes a second
argument whose type must agree. It is ceremony without benefit, and in Kotlin it
additionally breaks the `::publish` method reference the drain pattern uses.

## Consequences

Positive:

- Adding a domain event touches the aggregate, the event type and its consumers —
  never this port.
- The port has one reason to change (delivery semantics), not one per event type.
- The two-context split needs no per-context publisher.
- The drain idiom is one line and identical everywhere.

Negative / accepted trade-offs:

- The `publish` call site is not type-checked against a specific event. Accepted:
  the publisher does not read the event, and consumers are typed.
- `DomainEvent` becomes load-bearing. Any type implementing it is publishable, so
  the marker must not be applied to non-events. `architecture.definition.md` § 9
  already constrains what may live in `shared.domain.event`, and
  `ddd-hex-reviewer` checks the rest.
- A malformed event fails at delivery rather than at compile time. Mitigated by
  events being immutable `data class`es validated at construction
  (`modelling.definition.md`, Always-Valid).

## Future Considerations

- **Delivery guarantees.** ADR 0002 defers the Transactional Outbox pattern. If it
  is adopted, this signature is unaffected — serialising a `DomainEvent` to an
  outbox row is an adapter concern.
- **Event metadata.** Should events need envelope data (`occurredAt`, correlation
  id, causation id), prefer adding it to `DomainEvent` as properties over widening
  this function. That keeps the port at one function and makes the metadata
  available to every consumer. Note that adding a property to `DomainEvent` is a
  breaking change for every event type, which is an argument for doing it early if
  at all.
