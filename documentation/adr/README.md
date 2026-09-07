# Architectural Decision Records

Two tiers. The difference matters when you start a service from this template.

## Baseline — inherited from the template

Decisions that hold for **every** service built from this template. They arrive already
accepted, marked `Accepted (inherited from template)`. A service may supersede one, but it
does so knowingly and writes its own ADR to say so.

| ADR | Decision |
|-----|----------|
| [0002](0002-domain-event-publication.adr.md) | Domain events are published after commit |
| [0004](0004-generic-domain-event-publisher-signature.adr.md) | One generic publisher signature, not one method per event |
| [0005](0005-bounded-context-identity-boundaries.adr.md) | A foreign context's identity crosses as an opaque value |
| [0007](0007-archunit-boundary-enforcement.adr.md) | Boundaries are enforced by ArchUnit, not by review |
| [0009](0009-kotlin-spring-boot-stack.adr.md) | Kotlin 2.3 / Spring Boot 4 / PostgreSQL |
| [0010](0010-jvm-17-baseline.adr.md) | JVM 17 baseline |
| [0011](0011-persistence-annotations-stay-out-of-the-domain.adr.md) | The domain holds no persistence annotations; the ORM is a project choice |
| [0012](0012-dual-delivery-transports.adr.md) | REST and GraphQL as parallel transports, one core |
| [0013](0013-postgresql-and-testcontainers.adr.md) | PostgreSQL everywhere; Testcontainers for `*IT` |
| [0014](0014-quality-gates-are-executable.adr.md) | Every rule class has an executable owner |

## Example — belongs to the tour domain

Decisions about the worked example, not about the method. A new service **deletes these
along with the example** and writes its own.

| ADR | Decision |
|-----|----------|
| [0003](0003-separate-guide-bounded-context.adr.md) | `guide` is a separate bounded context |

ADR-0003 is kept rather than removed, even though it decides nothing about a new service.
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
