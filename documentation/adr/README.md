# Architectural Decision Records

Two tiers. The first arrived with the template this repository grew out of; the second is
this service's own.

A third tier used to hold decisions about the worked example. It is gone, along with the
example — see **Superseded and withdrawn** below, which is now the largest section in this
index and is the honest record of a scope change rather than an embarrassment.

**Summaries here describe each decision in the ADR's own terms.** That sounds like a
formatting note and is not. ADR-0019 was once written against this file's one-line summary of
ADR-0002 rather than against ADR-0002 itself; the summary had collapsed two different facts
into one clause, and the defect it produced took an ADR of its own to unpick. A summary is not
a source.

## Baseline — inherited from the template

Decisions that arrived already accepted, marked `Accepted (inherited from template)`. They
still hold. Superseding one is allowed and requires this service's own ADR saying so.

| ADR | Decision |
|-----|----------|
| [0002](0002-domain-event-publication.adr.md) | The driver publishes domain events **inside** its `@Transactional` boundary, through the `DomainEventPublisher` outport. Delivery timing is a property of the adapter behind that port |
| [0004](0004-generic-domain-event-publisher-signature.adr.md) | One generic publisher signature, not one method per event |
| [0005](0005-bounded-context-identity-boundaries.adr.md) | A foreign context's identity crosses as an opaque value; adding a member to the shared kernel requires an ADR. Its worked example, `TourId`, is gone and the reservation is unspent |
| [0007](0007-archunit-boundary-enforcement.adr.md) | Boundaries are enforced by ArchUnit, not by review |
| [0009](0009-kotlin-spring-boot-stack.adr.md) | Kotlin 2.3 / Spring Boot 4 / PostgreSQL |
| [0010](0010-jvm-17-baseline.adr.md) | JVM 17 baseline |
| [0011](0011-persistence-annotations-stay-out-of-the-domain.adr.md) | The domain holds no persistence annotations; the ORM is a project choice |
| [0012](0012-dual-delivery-transports.adr.md) | REST and GraphQL as parallel transports over one core, distinguished by class name so the role rules stay decidable. Narrowed by 0020; its worked example lapsed with the tour code |
| [0013](0013-postgresql-and-testcontainers.adr.md) | PostgreSQL everywhere; Testcontainers for `*IT` |
| [0014](0014-quality-gates-are-executable.adr.md) | Every rule class has an executable owner |

## This service — Contract Management

Decisions taken for this service. They are the ones a reader needs in order to understand
why the code looks the way it does.

| ADR | Decision |
|-----|----------|
| [0018](0018-lifecycle-transitions-belong-to-the-aggregate.adr.md) | A state change is a named method on the aggregate that enforces that transition's invariants; no state-machine framework |
| [0020](0020-graphql-as-the-only-transport.adr.md) | GraphQL is the only transport; REST and springdoc arrive with the first machine consumer |
| [0021](0021-operational-baseline-from-the-platform.adr.md) | Metrics, git hooks and a coverage report taken from the platform's other service; coverage reports, never blocks |
| [0024](0024-one-context-with-master-as-the-aggregate-root.adr.md) | One bounded context, `contract`. `Master` is the only aggregate root and `Contract` is an entity inside it, so there is one repository and deleting a master deletes its contracts |
| [0025](0025-reads-go-through-the-repository-outport.adr.md) | Reads use the same `MasterRepository` the writes use — no query port, no projection — with three written conditions for revisiting |
| [0027](0027-a-rule-with-no-possible-subject-is-deleted-not-allowed-to-pass-empty.adr.md) | A rule whose subject **cannot** exist is deleted and restored with the transport that gives it one; a rule with no subject **yet** keeps an allowance named for the condition that actually holds |

**No decision is currently owed.** The read-side ADR was the standing debt across every
earlier version of this index; 0025 pays it. Two items remain open and are *not* ADR debts —
they are unwritten specifications: authentication (`notes.md`) and the first outbound
integration, neither of which has a subject yet.

## Superseded and withdrawn

An accepted ADR's **text** is immutable. Its **status** is not: a status transition is how an
ADR records that the world moved on, and it is the only edit normally permitted.

**One exception was taken and is recorded rather than hidden.** ADR-0012's Consequences cited
a test method that was deleted with the example, and `SpecCitationsTest` fails on a citation
naming nothing. `CLAUDE.md`'s authority order puts `test.definition.md` § 9 at rank 6 and this
convention at rank 13, so the citation was replaced with a statement of what is now true. The
edit is declared in that ADR's own Status block.

| ADR | Status | Why |
|-----|--------|-----|
| [0001](0001-technical-stack.adr.md) | Superseded by 0009 | The Java 21 / jOOQ / H2 stack describes nothing here any more |
| [0006](0006-java-25-baseline.adr.md) | Superseded by 0010 | Baseline moved to JVM 17 |
| [0015](0015-two-contexts-by-contract-level.adr.md) | Superseded by 0024 | The `mlc`/`ilc` split answered a contract-level question the current scope does not pose |
| [0026](0026-the-empty-service-is-a-transient-state.adr.md) | Superseded by 0027 | Its retirement instruction assumed every relaxed rule would gain a subject with the first `contract` increment; five of them describe packages `adr/0020` forbids |
| [0003](0003-separate-guide-bounded-context.adr.md) | **Withdrawn** | The `guide` context and the tour example were deleted. 0024 is now the precedent a new-context ADR follows |
| [0008](0008-synchronous-cross-context-cancellation.adr.md) | **Withdrawn** | The use cases it governed were part of the example |
| [0016](0016-single-deployable-for-the-mvp.adr.md) | **Withdrawn** | One deployable or two, for services that are no longer in scope |
| [0017](0017-contract-data-ownership-boundary.adr.md) | **Withdrawn** | There are no external participant systems and no foreign identities in the current scope |
| [0019](0019-outbound-synchronisation-through-an-outbox.adr.md) | **Withdrawn** | Nothing to synchronise: the outbound systems left with the JCM scope |
| [0022](0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md) | **Withdrawn** | It decided a detail of 0019 |
| [0023](0023-participant-identities-are-context-local.adr.md) | **Withdrawn** | All three identity types were deleted with `mlc`; 0005's reservation is unspent again |

**Withdrawn** is reserved for an ADR whose *subject* no longer exists — nothing replaced it,
so "superseded" would be a lie. Note that 0015 is the one member of this scope change that is
genuinely **superseded**: 0024 answers the same question. The rest asked questions this
service has stopped asking.

None is deleted. Two are worth reading even withdrawn:
[0008](0008-synchronous-cross-context-cancellation.adr.md) is the best worked example here of
synchronous cross-context integration (`architecture.definition.md` § 11 rule 3, second form),
and [0022](0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md) documents the
summary-is-not-a-source failure that produced it.

Note what the superseding ADRs do **not** do: they do not edit 0001 or 0006. 0009 states which
of ADR-0001's reasons survived and which were reconsidered — its ban on JPA is the one that
was, and 0011 argues on what grounds.

## Writing a new one

Triggers are the canonical list in [`sdd.playbook.md` § 6](../sdd.playbook.md) — not copied
here. Naming is in [`file-naming.definition.md`](../file-naming.definition.md).
