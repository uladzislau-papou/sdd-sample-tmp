# Architectural Decision Records

Three tiers. The first two arrived with the template this repository grew out of; the third
is this service's own.

## Baseline — inherited from the template

Decisions that arrived already accepted, marked `Accepted (inherited from template)`. They
still hold. Superseding one is allowed and requires this service's own ADR saying so.

| ADR | Decision |
|-----|----------|
| [0002](0002-domain-event-publication.adr.md) | The driver publishes domain events **inside** its `@Transactional` boundary, through the `DomainEventPublisher` outport. Delivery timing is a property of the adapter behind that port — see 0022, which corrects a defect this row's earlier one-line summary ("published after commit") caused |
| [0004](0004-generic-domain-event-publisher-signature.adr.md) | One generic publisher signature, not one method per event |
| [0005](0005-bounded-context-identity-boundaries.adr.md) | A foreign context's identity crosses as an opaque value |
| [0007](0007-archunit-boundary-enforcement.adr.md) | Boundaries are enforced by ArchUnit, not by review |
| [0009](0009-kotlin-spring-boot-stack.adr.md) | Kotlin 2.3 / Spring Boot 4 / PostgreSQL |
| [0010](0010-jvm-17-baseline.adr.md) | JVM 17 baseline |
| [0011](0011-persistence-annotations-stay-out-of-the-domain.adr.md) | The domain holds no persistence annotations; the ORM is a project choice |
| [0012](0012-dual-delivery-transports.adr.md) | REST and GraphQL as parallel transports, one core |
| [0013](0013-postgresql-and-testcontainers.adr.md) | PostgreSQL everywhere; Testcontainers for `*IT` |
| [0014](0014-quality-gates-are-executable.adr.md) | Every rule class has an executable owner |

## This service — Contract Management

Decisions taken for this service. They are the ones a reader needs in order to understand
why the code looks the way it does.

| ADR | Decision |
|-----|----------|
| [0015](0015-two-contexts-by-contract-level.adr.md) | Two bounded contexts, `mlc` and `ilc`, split by contract level |
| [0016](0016-single-deployable-for-the-mvp.adr.md) | One deployable for the MVP, though the component view draws two services |
| [0017](0017-contract-data-ownership-boundary.adr.md) | The contract is ours; its participants are external and enter through an anti-corruption layer |
| [0018](0018-lifecycle-transitions-belong-to-the-aggregate.adr.md) | Transitions are aggregate methods; no state-machine framework |
| [0019](0019-outbound-synchronisation-through-an-outbox.adr.md) | Synchronisation to Radar and Odoo goes through an outbox |
| [0020](0020-graphql-as-the-only-transport.adr.md) | GraphQL is the only transport in the MVP; REST arrives with a machine consumer |
| [0021](0021-operational-baseline-from-the-platform.adr.md) | Metrics, git hooks and a coverage report taken from the platform's other service; coverage reports, never blocks |
| [0022](0022-the-outbox-row-is-written-inside-the-callers-transaction.adr.md) | The outbox row is written by the `DomainEventPublisher` adapter inside the caller's transaction; a separate relay dispatches |
| [0023](0023-participant-identities-are-context-local.adr.md) | `EmployerId`, `LessorId` and `PartnerNumber` are Value Objects local to `mlc`, not promoted to `shared.domain`. ADR-0005 reserved the choice to an ADR; its category-3 test needs a second context that does not exist yet |

One decision is **owed and not yet written**: the read side. The MVP's display requirements
need query ports or projections, `project.definition.md` records the absence as a Non-Goal,
and the first display use case writes the ADR. The three use cases sequenced ahead of it are
all writes, which is why this is a written debt rather than a gap.

## Example — belongs to the tour domain

Decisions about the worked example, not about the method. A new service **deletes these
along with the example** and writes its own.

| ADR | Decision |
|-----|----------|
| [0003](0003-separate-guide-bounded-context.adr.md) | `guide` is a separate bounded context |

ADR-0003 becomes **Withdrawn** when the tour-booking example leaves the tree, and
0015 takes over its role as the precedent for "a new bounded context requires an ADR". Until
then it is kept rather than removed, even though it decides nothing about this service.
It is the precedent `sdd.playbook.md` § 6 points at for "a new bounded context requires an
ADR", it is the ADR column of `architecture.definition.md` § 11 for the `guide` row, and
ADR-0005 cites it — and an accepted ADR's text is immutable, so that citation cannot be
edited away. A rule whose only worked example has been deleted is a rule that will be
applied wrongly.

## Superseded and withdrawn

An accepted ADR's **text** is immutable. Its **status** is not: a status transition is how
an ADR records that the world moved on, and it is the only edit permitted.

| ADR | Status | Why |
|-----|--------|-----|
| [0001](0001-technical-stack.adr.md) | Superseded by 0009 | The Java 21 / jOOQ / H2 stack describes nothing here any more |
| [0006](0006-java-25-baseline.adr.md) | Superseded by 0010 | Baseline moved to JVM 17 |
| [0008](0008-synchronous-cross-context-cancellation.adr.md) | **Withdrawn** | The use cases it governed are not part of the example |

**Withdrawn** is reserved for an ADR whose *subject* no longer exists — nothing replaced
it, so "superseded" would be a lie. It is not deleted, because the reasoning is the best
worked example this repository has of synchronous cross-context integration
(`architecture.definition.md` § 11 rule 3, second form).

Note what the superseding ADRs do **not** do: they do not edit 0001 or 0006. 0009 states
which of ADR-0001's reasons survived and which were reconsidered — its ban on JPA is the one
that was, and 0011 argues on what grounds.

## Writing a new one

Triggers are the canonical list in [`sdd.playbook.md` § 6](../sdd.playbook.md) — not copied
here. Naming is in [`file-naming.definition.md`](../file-naming.definition.md).
