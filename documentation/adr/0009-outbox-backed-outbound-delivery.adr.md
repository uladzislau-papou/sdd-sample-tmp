# ADR 0009 – Outbox-Backed Outbound Delivery for Client and Partner Callbacks

## Status

Accepted (retroactive)

## Context

RMS must push state outward to systems it does not control:

- **client callbacks** — `stateChangeCallbackUrl` on an identification or signature
  session,
- **ONB progress delivery** — KYC milestones (`PARTIES_PENDING`, `PARTIES_READY`,
  `SCREENING_CLEARED`, …) delivered to RADar,
- **RADar decision delivery** — terminal KYC decisions,
- **KYC research polling** — pulling provider results on a schedule.

These targets are unreliable and slow. Calling them inline from the transaction that
caused the state change would mean:

- a slow partner holding a database transaction open,
- a partner outage rolling back a legitimate KYC decision,
- a lost notification if the process dies after commit,
- and, for progress events, **out-of-order or duplicate delivery**, which for a
  milestone stream is a correctness bug rather than a nuisance.

ADR 0008 solved this for audit. The same shape applies here, with an extra requirement:
ordering.

## Decision

**Every outbound delivery to an external system goes through a durable outbox row and a
scheduled delivery service.**

| Concern | Table | Service | Scheduler |
|---------|-------|---------|-----------|
| Client callbacks | `webhook_deliveries` (`common/webhook/`) | `qes/service/webhook/` | `WebhookDeliveryScheduler` |
| ONB progress | KYC case delivery stamps + progress rows | `OnbProgressDeliveryService` | `OnbProgressDeliveryScheduler` |
| RADar decisions | RADar delivery rows | `KycRadarDeliveryService` | `KycRadarDeliveryScheduler` |
| Research polling | `kyc_research_poll_jobs` | `KycResearchPollService` | `KycResearchPollScheduler` |

Rules:

1. **The outbox row is written inside the business transaction.** If the state change
   rolls back, the promise to deliver goes with it.
2. **Delivery is a separate transaction, retried.** A partner outage delays delivery; it
   never fails the operation.
3. **A scheduler contains no business logic.** It selects due rows and delegates to the
   delivery service (`architecture.definition.md` § 4.4).
4. **Ordering, where it matters, is explicit.** `OnbProgressDeliveryOrder` defines the
   milestone sequence; a later milestone is not delivered before an earlier one. This is
   the requirement that distinguishes progress delivery from audit emission.
5. **Idempotency is a delivery-time property.** Milestone delivery stamps on `KycCase`
   (`partiesPendingDeliveredAt`, `partiesReadyDeliveredAt`, `screeningClearedDeliveredAt`)
   guarantee at most one delivery per milestone per case. The stamp is the dedup key.
6. **Delivery state is a column, not a log line.** Status, attempt count and last error
   live on the row.

## Consequences

**Positive**

- Partner availability is decoupled from RMS availability. A RADar outage delays delivery
  and nothing else.
- No transaction is held open across a network call to a third party.
- Delivery problems are queryable — `SELECT` on status tells an operator what is stuck and
  why.
- Milestone ordering is enforced by design rather than by hoping the scheduler happens to
  run in sequence.

**Negative / accepted**

- **Four outbox mechanisms with different shapes.** `webhook_deliveries` is a generic
  table; ONB progress uses delivery stamps on `KycCase`; RADar decisions and research
  polling have their own rows. A unified outbox would be tidier, but the ordering and
  dedup requirements genuinely differ, and unifying them is a large change for a
  consistency benefit rather than a correctness one.
- **At-least-once, not exactly-once.** A crash between the partner accepting and RMS
  recording success re-delivers. Receivers must deduplicate. The milestone stamps make
  this safe for progress; for client callbacks it is the consumer's responsibility, and
  the integration spec must say so.
- **Latency is bounded by scheduler interval, not by the event.** Notification is not
  real-time and must not be specified as if it were.
- **Poison rows.** A permanently rejected delivery exhausts its retries and stops. There
  is no dead-letter workflow; somebody has to look at the table.
- **Single-instance assumption.** Schedulers select due rows without distributed locking.
  Running two instances would double-deliver. Making the deployment horizontally scalable
  requires solving this first, and that is trigger 3.
- Delivery stamps live on `KycCase`, which mixes delivery bookkeeping into the business
  entity. Accepted because the stamp *is* the idempotency guarantee and belongs with the
  thing it deduplicates.

## Constraints

- A new outbound delivery to an external system uses this pattern. Inline calls from a
  business transaction to a third party are drift.
- Every delivery needs an integration spec
  (`documentation/integrations/*.outbound.spec.md`) stating the retry policy, the
  idempotency key, and what happens after the final attempt.
- Idempotency has a test: the same input twice produces one effect
  (`documentation/integrations/integration.spec.template.md` § 10).

## Alternatives considered

- **Inline synchronous calls after commit.** Rejected — loses the delivery on crash, the
  window that matters most.
- **A message broker (SQS, Kafka).** Rejected as premature. It adds infrastructure
  (trigger 7) and would still need an outbox to bridge the transaction, so it replaces the
  scheduler rather than the table. Reconsider if volume or the multi-instance requirement
  forces it.
- **A single unified outbox table for all four concerns.** Rejected for now: ordering and
  dedup semantics differ per stream, and forcing them into one schema would push that
  variation into a discriminator column and conditional logic.
