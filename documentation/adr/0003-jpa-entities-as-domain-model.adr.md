# ADR 0003 – JPA Entities as the Domain Model

## Status

Accepted (retroactive)

## Context

RMS persists regulated state: KYC cases and their status history, identifications,
signature sessions, screening results, outbox rows. Roughly forty tables.

The classical DDD position is that the domain model should be persistence-ignorant, with a
separate persistence model and a mapping layer between them. That gives constructor-time
invariant enforcement ("always-valid") and freedom to change the schema without changing
the model.

The alternative is to let the JPA entity *be* the domain model.

## Decision

**`<module>/domain/model/*.kt` classes are JPA entities.** `@Entity`, `@Table`, `@Column`,
relations, `@Version` and Hibernate timestamp annotations live on the same classes that
carry business state. There is no separate persistence model and no mapping layer between
domain and database.

Consequences for where behaviour lives (`modelling.definition.md` § 1):

- **Validity is enforced at the boundary and in services, not in constructors.** Hibernate
  instantiates by reflection and populates field by field; a constructor guard would fire
  on a half-built object or not at all.
- **State transitions are service methods**, not entity methods, whenever they consult
  another row, the clock, or configuration.
- **Entity methods are limited to what the row's own fields can answer.**
- Illegal state is prevented by the transition (the state machine, or the owning service),
  not by the type.
- Concurrency safety comes from `@Version` optimistic locking, not from an aggregate lock.

## Consequences

**Positive**

- One model. A schema change is one edit, not three plus a mapper test.
- No mapping layer to keep in sync, and no class of bug where the mapper and the schema
  disagree.
- Hibernate's dirty checking, lazy loading and optimistic locking are available directly.
- Onboarding cost is low: the entity *is* the table, and the KDoc on it documents both.

**Negative / accepted**

- **No always-valid guarantee.** An entity can hold any value the column allows. Every
  rule must be enforced somewhere explicit, and the enforcement is a convention rather
  than a type-system fact. `modelling.definition.md` § 3.1 is the compensating rule: a
  rule reachable from more than one inbound surface must live in the service, because
  GraphQL, REST, webhooks and schedulers all converge there and nothing else does.
- **The model is anemic** in the DDD sense. Business logic concentrates in services. This
  is a real cost — it makes services larger and makes "where is this rule?" a question
  answered by search rather than by structure. `kyc/service/` is sub-packaged by concern
  specifically to keep that search short.
- **Schema and model co-evolve.** A column rename is a model change. Flyway
  (ADR 0011) is the only sanctioned path, and an applied migration is never edited.
- **Lazy-loading hazards** cross the transaction boundary. Relations default to `LAZY`;
  accessing one outside a transaction throws. Mapping to a DTO inside the service's
  transaction is the discipline that avoids it.
- **Kotlin `data class` equality compares all properties**, which is not identity equality.
  `modelling.definition.md` § 2.1 requires comparing ids explicitly rather than relying on
  it.
- **Nullability must match the column exactly.** A non-null Kotlin type over a nullable
  column holds `null` at runtime with no error — Hibernate populates reflectively, past
  the compiler's guarantees. `coding-style.definition.md` § 2.4 makes a mismatch drift.

## Alternatives considered

- **Separate domain and persistence models with a mapping layer.** Rejected. Forty tables
  would become eighty classes plus forty mappers plus their tests, and the invariants it
  would buy are enforceable in services at a fraction of the cost. Revisit only if the
  domain rules grow complex enough that "which service enforces this?" stops having a
  quick answer.
- **Domain model with JPA annotations but hand-written factory methods enforcing
  invariants.** Rejected as the worst of both: the guards do not run on the reflective
  path Hibernate actually uses, so they would give the appearance of always-valid without
  the property.
- **jOOQ or plain JDBC with explicit mapping.** Rejected — it re-introduces the mapping
  layer while giving up dirty checking and optimistic locking.
