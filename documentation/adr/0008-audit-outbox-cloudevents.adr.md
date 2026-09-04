# ADR 0008 – Audit Trail via Transactional Outbox and CloudEvents

## Status

Accepted (retroactive)

## Context

RMS performs regulated actions: KYC accept and decline, identification outcomes, signature
completions, document access. These must produce an audit trail that:

- **survives** — an audit event must not be lost if the emit target is down,
- **does not break the business operation** — a failing audit sink must not roll back a
  legitimate KYC decision,
- **names the human** — "the system declined this case" is not an audit trail,
- **is consumable by the platform's audit kernel** in a standard envelope,
- **does not itself leak personal data.**

The two obvious approaches both fail one of the first two requirements. Emitting
synchronously inside the transaction makes the sink a dependency of every regulated
operation. Emitting after commit, in-process, loses the event if the process dies between
commit and emit.

## Decision

**A transactional outbox with CloudEvents envelopes and a scheduled emitter.**

```
business @Transactional
    └─ AuditEventPublisher → AuditOutboxAppender → INSERT audit_outbox   (same transaction)
                                                          │
                                              commit ─────┤
                                                          ▼
                        AuditEmitScheduler → AuditEmitService → audit sink
```

Components in `common/audit/`:

| Class | Role |
|-------|------|
| `AuditEvent`, `AuditTrigger`, `AuditDataKeys` | The event vocabulary |
| `CloudEvent`, `CloudEventFactory` | Envelope construction |
| `AuditEventPublisher` | The API services call |
| `AuditOutboxAppender`, `AuditOutboxEntryFactory`, `AuditOutboxRecorder` | Enqueue |
| `AuditOutboxEntry`, `AuditOutboxStatus`, `AuditOutboxRepository` | The `audit_outbox` table |
| `AuditEmitScheduler`, `AuditEmitService` | Delivery with retry |
| `AuditActors` | Actor identifiers, including `svc:risk-management-service` |
| `EmailHasher` | Personal-data protection |

Rules:

1. **The outbox write joins the business transaction.** If the business change rolls back,
   the audit row goes with it. There is no event for something that did not happen.
2. **Emission is separate and retried.** A sink outage delays the trail; it never fails a
   KYC decision.
3. **Envelope is CloudEvents**, validated in tests against
   `src/test/resources/audit/cloudevents.schema.json`.
4. **Attribution** is the `OperatorContext` operator, or `svc:risk-management-service`
   (ADR 0006).
5. **Personal data is hashed or omitted.** `EmailHasher` for emails; identifiers and
   statuses otherwise.
6. **Events are catalogued** in `docs/audit-kernel.md` and
   `docs/kyc/kyc-event-catalogue.md` in the service repository. A new or changed event
   updates the catalogue in the same increment (`test.definition.md` § 7 gate 14).

## Consequences

**Positive**

- The audit trail is exactly as durable as the business data, because it is the same
  transaction.
- Sink availability is decoupled from operation availability.
- One envelope format for the platform's audit kernel to consume.
- `audit_outbox` rows carry status and attempt count, so a delivery problem is visible in
  the database rather than only in logs.

**Negative / accepted**

- **The trail is eventually consistent.** An event exists in the outbox before it reaches
  the sink. An auditor reading the sink sees a lag bounded by the scheduler interval plus
  retry backoff — not by anything stronger.
- **At-least-once delivery.** A crash between emit and status update re-delivers. The sink
  must deduplicate on the CloudEvent id; RMS does not guarantee exactly-once.
- **Poison entries.** An event the sink permanently rejects retries until the policy gives
  up, and then sits in a terminal failure state. Somebody has to look. There is no
  dead-letter workflow beyond the status column.
- **Table growth.** `audit_outbox` accumulates. Retention is not addressed by this ADR and
  is an open operational question.
- **The scheduler is a single point of delivery** in a single-instance deployment. Multiple
  instances would need locking on the outbox rows; that has not been designed.
- Hashing is one-way: an audit trail cannot be joined back to a person without the
  original value. That is the intended privacy property and also a limitation for
  investigation.

## Constraints

- **Changing the audit contract — envelope, transport, or guarantee — is an ADR trigger**
  (`sdd.playbook.md` § 6 item 16). *Adding* an event to the existing contract is a
  catalogue update, not an ADR.
- Every new or changed audit event has a schema-validated test
  (`test.definition.md` § 2.6).
- An audit event MUST NOT be emitted outside the transaction that justified it, and MUST
  NOT be written by a Spring application listener as a substitute
  (`modelling.definition.md` § 5).

## Alternatives considered

- **Synchronous emission inside the transaction.** Rejected — makes the audit sink a hard
  dependency of every regulated operation.
- **`@TransactionalEventListener(AFTER_COMMIT)` emitting in-process.** Rejected — loses
  events on crash between commit and emit, which is exactly the window that matters.
- **Application-log-based audit** scraped downstream. Rejected — no transactional
  guarantee, no schema, and logs are the one place personal data must not go.
- **A message broker instead of an outbox table.** Rejected as premature: it adds
  infrastructure (trigger 7) to solve a delivery problem the database already solves at
  this volume.
