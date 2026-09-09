# ADR 0019 – Outbound Synchronisation Through an Outbox

## Status
**Withdrawn** — its subject no longer exists.

The outbox existed to synchronise contract data to Radar and Odoo. There are no outbound
systems in the current scope, so there is nothing to synchronise and no outbox. `adr/0002`
governs domain-event publication and is untouched.

Withdrawing this also withdraws `adr/0022`, which decided *how* the outbox row was written.

## Context

The MVP requires data synchronisation with Radar and Odoo for its use cases, and the risk
register calls for the synchronisation strategy to be fixed before the MVP starts, with a
proof exercise in both directions: carry one real contract out of Radar, and play one
contract back into Radar and Odoo.

`adr/0017-contract-data-ownership-boundary.adr.md` establishes that contract facts are ours.
Therefore we produce changes that those systems need to learn about.

The naive implementation calls their APIs from the use case. That places a foreign system
inside our transaction: their timeout rolls back our contract change, or our commit succeeds
and their call does not, and the divergence has no record.

## Decision

Outbound synchronisation goes through an **outbox**. A use case that changes a contract
writes its own state and an outbox record in the **same transaction**. A separate dispatcher
reads pending records, calls the foreign system, and marks the outcome.

Consequences of the pattern that are part of the decision:

- The domain publishes a domain event; the outbox appender is an adapter subscribed to it,
  not something a use case calls. Domain events are published after commit
  (`adr/0002-domain-event-publication.adr.md`), so the appender writes inside the same
  transaction as the change while the *dispatch* happens afterwards.
- Records carry a status and an attempt count. A permanently failing record stays visible
  rather than disappearing into a log.
- Delivery is at-least-once. The receiving side's idempotency is a question for the
  integration spec, not an assumption.

**What is not decided here**: which fields go to Radar, which go to Odoo, in what shape, and
on which events. That is the synchronisation strategy the project has not settled, and it
belongs in an outbound port spec under `documentation/ports/` once it is answered. This ADR
fixes the mechanism, so that the answer lands in one place instead of inside five use cases.

## Rationale

**It is the only mechanism that keeps our transaction ours.** The alternative — a foreign
call inside the transaction — makes our contract's consistency depend on someone else's
uptime, in a domain where the contract's status is the thing of record.

**The mechanism is needed before the strategy is known.** The MVP's first use cases create
contracts, and something must be recorded for the eventual sync even while the target
mapping is undecided. An outbox record with a domain event in it can be replayed once the
mapping exists; an unrecorded change cannot be recovered at all.

**The donor service has the pattern already.** Its audit outbox — entries, statuses, a
recorder, a dispatcher — is a worked implementation to read rather than a design to invent.
It is a reference, not a dependency: nothing is copied wholesale into this service, because
its schema serves a different purpose.

## Consequences

- One table, one dispatcher, and operational visibility on both. Nothing consumes it in the
  MVP beyond the sync itself.
- Audit is not a feature here, but the foundation for it exists as a side effect. If audit
  is later required, it lands on this mechanism rather than beside it.
- A use case's specification must name the events it emits, because those events are the
  sync's input. `conformance-reviewer` checks a spec's criteria against tests, so an emitted
  event that no test observes is visible as an unmet criterion.
