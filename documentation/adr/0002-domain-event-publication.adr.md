# ADR 0002 – Domain Event Publication Strategy

## Status

Accepted

## Context

`modelling.definition.md` states:

> Application layer is responsible for publishing events AFTER successful transaction commit.

That sentence settles *when* but not *how*. Three questions were open:

1. Where do events accumulate between being raised and being published?
2. What guarantees delivery after the transaction commits?
3. What happens to a consumer's work — does it join the publisher's transaction
   or run in its own?

The answers matter more than usual here because one event, 
`MasterLeasingContractActivated`, drives a cross-context fan-out (UC06): 
activating a master contract activates every pending lease beneath it.

## Decision

**Aggregates record events; drivers drain and publish them after the transaction
commits; consumers run in a new transaction.**

Concretely:

1. An aggregate holds a private list of `DomainEvent` and appends to it inside each
   state-transition function. It never publishes.
2. The aggregate exposes `pullDomainEvents(): List<DomainEvent>`, which returns an
   immutable snapshot **and clears the list**. A second call returns empty.
3. The driver drains after persisting, inside its transaction:
   ```kotlin
   contract.pullDomainEvents().forEach(domainEventPublisher::publish)
   ```
4. `DomainEventPublisher` is a `shared.outport` interface (ADR 0004). Its adapter,
   `LoggingDomainEventPublisher`, delegates to Spring's `ApplicationEventPublisher`.
5. Consumers annotate `@TransactionalEventListener(phase = AFTER_COMMIT)` with
   `@Transactional(propagation = REQUIRES_NEW)`.

## Rationale

### Why the aggregate records rather than publishes

An aggregate that publishes needs a publisher, which means a collaborator, which
means either constructor injection into a domain object or a service locator.
Both put infrastructure inside `core.domain`, which
`architecture.definition.md` § 4.1 forbids and which would make the aggregate
untestable without a stub.

Recording is the only option that leaves the aggregate a pure object. The cost is
that something must remember to drain — see *Consequences*.

### Why draining clears the list

Because the alternative silently double-publishes. If `pullDomainEvents` were a
plain getter, a driver that loaded, mutated, and mutated again would publish the
first event twice. Clearing makes the drain a transfer of ownership rather than a
read, and makes "was this published?" a question with one answer.

### Why `AFTER_COMMIT`

An event published before commit is a claim about a fact that may not survive.
A downstream consumer acting on `MasterLeasingContractActivated` for a transaction
that then rolls back would activate leases under a contract that is still pending.

`AFTER_COMMIT` means a rollback publishes nothing. The converse — a commit whose
event is then lost — is discussed under *Known limitation*.

### Why `REQUIRES_NEW` on the consumer

An `AFTER_COMMIT` listener runs after the publisher's transaction has committed, so
there is no transaction to join. Without `REQUIRES_NEW`, a consumer's repository
call would run in auto-commit mode, one statement at a time — so a fan-out that
failed halfway would leave half the leases activated and no way to tell.

`REQUIRES_NEW` gives the consumer its own atomic unit. UC06 activates every pending
lease under a contract in that one transaction
(`architecture.definition.md` § 10).

### Why not an outbox, and why not a broker

Both were considered and deferred.

A **transactional outbox** would close the known limitation below by writing the
event to a table inside the same transaction as the state change, and delivering it
from there. It is the correct answer for a system with external consumers. It is
deferred because every consumer today is in-process: the outbox would add a table,
a poller, a delivery-attempt column and a backoff policy to make in-memory
publication reliable against a failure mode that costs one pending lease.

A **message broker** (Kafka, Rabbit) is a separate ADR trigger
(`sdd.playbook.md` § 6 item 7) and is out of scope while there is one process.

## Known limitation

**Delivery is not guaranteed.** If the JVM dies between commit and the listener
running, the event is lost with no record that it existed. For UC06 that means a
lease stays `PENDING_ACTIVATION` under an `ACTIVE` master contract, and nothing
retries.

This is accepted, and it is accepted *because of which fan-out uses events*.
`architecture.definition.md` § 10 splits the two: activation is eventual and
recoverable, so it goes through an event; cancellation is not, so it goes through a
shared transaction and never touches this mechanism. The limitation therefore
cannot produce the dangerous state — a cancelled master contract with live leases
under it — because cancellation was deliberately kept off this path.

Recovering a missed activation is a manual re-trigger today. An outbox is the fix
when that stops being acceptable.

## Consequences

Positive:

- `core.domain` has no infrastructure dependency.
- A rollback cannot leak an event.
- Consumers fail independently of publishers.
- The drain pattern is identical in every driver, so it reads as one idiom rather
  than as a decision per use case.

Negative / accepted trade-offs:

- **A driver that forgets to drain loses the events silently.** Nothing structural
  prevents it; the use-case test asserting the publication is what catches it, which
  is why `test.definition.md` § 2.2 lists emitted events as mandatory coverage.
- Delivery is best-effort (above).
- `@TransactionalEventListener` is Spring-specific. It lives in `inbound.listener`,
  which is an adapter package, so the coupling is where it belongs — but a move off
  Spring would rewrite every listener.
