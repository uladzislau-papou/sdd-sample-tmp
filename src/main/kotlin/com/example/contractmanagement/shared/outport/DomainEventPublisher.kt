package com.example.contractmanagement.shared.outport

import com.example.contractmanagement.shared.domain.event.DomainEvent

/**
 * Cross-context outbound port for handing off domain events raised by a use case.
 *
 * **Must be called inside the caller's active transaction**, and that is the port's whole
 * contract. Where the call happens is `adr/0002-domain-event-publication.adr.md`'s decision:
 * the driver publishes within its `@Transactional` boundary.
 *
 * **When and how delivery happens is a property of the adapter, not of this port.**
 * `adr/0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md` decides it: the
 * adapter writes an outbox row synchronously in the ambient transaction and performs no
 * outbound call, while a separate scheduled relay dispatches. So the row commits with the
 * aggregate or not at all, which is the atomicity an outbox exists for.
 *
 * This KDoc previously said the implementation "is responsible for delivering the event after
 * commit (ADR 0002)". That conflated **where publication is called** with **when delivery
 * happens** — exactly the collapse that made `adr/0019` cite `adr/0002` for after-commit
 * publication and so specify an outbox that could not be atomic. ADR-0022 was written to
 * correct that defect and did not propagate to the port it corrected;
 * `HANDOFF.md` § 5 names the failure class as *a summary is not a source*, and predicted a
 * fourth victim. This was it.
 *
 * **The adapter ADR-0022 describes does not exist yet.** The bean wired behind this port is
 * `LoggingDomainEventPublisher`, which — despite its name — logs nothing itself: it delegates
 * to Spring's `ApplicationEventPublisher`, and a `@TransactionalEventListener(AFTER_COMMIT)`
 * receives the event after the commit. The *listener* is what logs, which is where the name
 * comes from. That is `adr/0002`'s reference adapter, and it is
 * precisely the one `adr/0022` replaces, because a listener subscribed that way would write
 * an outbox row *after* the commit and lose the atomicity an outbox exists for.
 *
 * So today's behaviour genuinely *is* post-commit delivery. What "do not assume post-commit"
 * means is that callers must not depend on it, because the adapter is being replaced and the
 * port never promised it.
 *
 * The first version of this KDoc said the adapter "logs", which is false — it was inferred
 * from the class name rather than read from the class. `ddd-hex-reviewer` caught it, and the
 * irony is the point: this correction is itself an instance of *a summary is not a source*,
 * committed inside the KDoc written to correct one.
 *
 * Callers owe what is written above regardless — UC07's AC-04 asserts it — and building the
 * outbox is its own increment, with the record's content deferred to an outbound port spec as
 * `adr/0019` directs.
 *
 * The interface is framework-free; the Spring integration lives entirely in the adapter.
 *
 * SDD: see `documentation/ports/domain-event-publisher.outport.spec.md`.
 */
interface DomainEventPublisher {
    /**
     * Hands off [event] for delivery.
     *
     * Call inside an active transaction. Delivery timing belongs to the adapter — see the
     * type KDoc, and do not assume post-commit.
     */
    fun publish(event: DomainEvent)
}
