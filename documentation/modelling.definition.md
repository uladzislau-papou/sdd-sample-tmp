# Modelling Definition

How Risk Management Service models its business concepts, so modelling stays consistent,
reviewable and reviewable *mechanically*.

**Read `architecture.definition.md` § 2 first.** RMS is a layered modular monolith whose
domain layer is JPA entities. This document defines modelling doctrine *for that shape*,
not for a Ports & Adapters core. Where classical DDD advice would contradict the shape,
this document says which one wins.

---

# 1. The Model Is Persistent

**An RMS domain class is a JPA entity.** `@Entity`, `@Table`, `@Column`, relations,
`@Version`, Hibernate timestamps — all of it lives on the same class that carries the
business state.

This is a deliberate trade recorded in `adr/0003-jpa-entities-as-domain-model.adr.md`.
It buys one model instead of two plus a mapping layer; it costs the ability to enforce
"always-valid" at construction, because Hibernate constructs instances by reflection and
populates them field by field.

What follows from that, and is therefore doctrine here:

- **Validity is enforced at the boundary and in services, not in constructors.**
  Bean Validation on `api.input` types guards the inbound edge; the service guards the
  transition. An entity's `init` block may assert structural facts, but it is not the
  primary guard and must not assume a full object graph.
- **State transitions are service methods, not entity methods**, whenever they need to
  consult another row, the clock, or configuration. An entity method that could be
  written using only its own fields SHOULD live on the entity.
- **Illegal state is prevented by the transition, not by the type.** A `KycCase` can hold
  any `KycCaseStatus` the database holds; what stops an illegal one is
  `KycCaseStateMachineService` refusing the transition.

Anyone reaching for "the aggregate should enforce this invariant" must first check
whether it can be enforced with the entity's own fields. If it cannot, the service is the
correct home and that is not a compromise.

---

# 2. Building Blocks

## 2.1 Entity

**Definition:** a persistent business object with database identity.

Rules:

- `@Entity data class` with a `UUID` `@Id`, `@GeneratedValue`.
- Equality is by `id` in business terms. Kotlin `data class` equality compares all
  properties — do not rely on it for identity; compare ids explicitly.
- Mutable state uses `var`; immutable columns use `val`. See § 2.2.
- Class KDoc documents **every** property, including what `null` means for nullable ones
  (`coding-style.definition.md` § 1.4). `KycCase` is the reference for the standard.
- MUST NOT call a repository, an HTTP client, or a scheduler.
- MUST NOT read the clock to make a decision.

## 2.2 Mutability

- A column the business never changes after insert is `val` (`id`, `createdAt`,
  `caseNumber`, foreign keys that define the row's identity).
- A column advanced by a lifecycle is `var`.
- **A `var` column that participates in a lifecycle MUST only be written by the service
  that owns that lifecycle.** Two services writing `KycCase.status` is a modelling error,
  not a coordination problem.
- Entities whose status is advanced from more than one path carry `@Version`. Optimistic
  locking is how concurrent illegal transitions are rejected rather than silently
  last-writer-wins.

## 2.3 Value types

RMS uses primitives and enums for most values. That is the convention; a `String`
company name is not drift.

Introduce a dedicated type when **all** of the following hold:

1. the value has validation rules that would otherwise be re-checked at several sites,
2. it is passed through more than one layer, and
3. mixing it up with another value of the same primitive type is a plausible bug.

Otherwise: constrain it with Bean Validation at the boundary and keep the primitive.

Enums are mandatory for closed value sets that appear in a column, a GraphQL schema, or a
provider translation table. A status held as a `String` is drift.

## 2.4 Aggregate-shaped entities

RMS does not have formal aggregates, but some entities are consistency anchors:
`KycCase`, `Company`, `Identification`, `SignatureSession`, `BoniDecision`,
`AuditOutboxEntry`, `WebhookDelivery`.

For those:

- One repository, one owning service (or one owning package of services).
- Child rows (`KycCaseSignee`, `KycUbo`, `SignatureSessionSigner`, …) are loaded and
  written through the anchor's owning service, not by unrelated modules.
- A transaction changes one anchor's state. Changing two anchors' lifecycle states in one
  transaction requires a spec that says why.

## 2.5 Service

**Definition:** the unit of business behaviour. A `@Service` class.

Responsibilities:

- Own the transaction
- Load, decide, mutate, persist
- Enforce transition legality
- Emit audit events, enqueue outbound deliveries
- Translate provider results into our vocabulary

Rules:

- A service method that spans several sub-domains delegates rather than inlining. Name
  the collaborators; do not grow a god service.
- A service MUST NOT be constructed with a controller, a mapper that needs IO, or another
  module's controller.
- **Self-invocation does not go through the proxy.** A `@Transactional` method called from
  within the same class is not transactional. Split the class or inject a collaborator.
- A service that only reads is a `*QueryService` and is `@Transactional(readOnly = true)`.

## 2.6 State machine

`kyc` models the case lifecycle with Spring Statemachine
(`kyc/statemachine/`, `adr/0007-kyc-case-state-machine.adr.md`).

Rules:

- **Transition legality is declared in the state-machine configuration, not scattered
  through services.** A service that hand-checks a status before calling the machine is
  duplicating the machine's job — unless the check produces a *different* error message
  the API contract requires, in which case the duplication is deliberate and documented
  (`KycCaseDecisionService.collectParties` is the precedent).
- A new status or event is a change to the machine configuration first, the enum second,
  and a Flyway migration third if it is persisted.
- Every transition that matters to a regulator emits an audit event.

## 2.7 Mapper

Stateless translation. Entity → DTO, provider model → our model, input → entity.

Rules:

- Pure. No repository, no clock, no IO. A mapper that needs one of those is a service.
- A mapper is the **only** place a provider's vocabulary is allowed to meet ours.
- Mapping is total: an unmapped enum case is a failure, not a silent `null`. Prefer an
  exhaustive `when` with no `else`.

## 2.8 Repository

Spring Data JPA interface. See `architecture.definition.md` § 4.3.

- Method names use business language: `findByCaseIdOrThrow`, `findConfirmedByCompanyId`.
- A query that encodes a business criterion states that criterion in KDoc. The service
  still enforces the rule — the query selects candidates, it does not replace the guard.

## 2.9 Outbox entry

An outbox row is a **promise to deliver**, not a domain fact.

- Written inside the business transaction, delivered by a scheduler afterwards.
- Carries its own status, attempt count and error, so a delivery failure is visible
  without reading logs.
- Idempotency is the receiver's contract *and* ours: an entry that may be delivered twice
  must carry a stable key the receiver can deduplicate on.
- Kinds in use: `audit_outbox` (`common.audit`), `webhook_deliveries`
  (`common.webhook`), the ONB progress and RADar decision deliveries (`onb`).

---

# 3. Validity

## 3.1 Where validation happens

| Layer | What it validates | Mechanism |
|-------|-------------------|-----------|
| `api.input` | Shape, format, required fields, ranges | Bean Validation (`@Valid`, constraints in `validation/`) |
| `api.controller` | Nothing beyond `@Valid` | — |
| `service` | Transition legality, cross-row rules, authorization of the operation | Explicit checks, state machine |
| `domain.model` | Structural facts the row alone can assert | `init` block (sparingly) |
| database | Uniqueness, nullability, foreign keys | Flyway DDL constraints |

Each layer's job is distinct. A rule enforced only by the database is a rule with a
500-shaped error message; a rule enforced only at the boundary is a rule a scheduler or
webhook path can bypass.

**A rule reachable from more than one inbound surface MUST be enforced in the service.**
GraphQL, REST, webhook and scheduler all converge there; nothing else does.

## 3.2 Rehydration

Loading a row that no longer satisfies a current rule MUST NOT be silently accepted into a
new decision. Handle it explicitly: a Flyway data migration, a repair path, or an explicit
guard that fails loudly. Silent acceptance of stale-invalid state is forbidden.

---

# 4. Errors

## 4.1 Taxonomy

The base types live in `qes.domain.exception` and are used by every module
(`architecture.definition.md` § 4.2, § 11.3):

| Type | Meaning | Surfaced as |
|------|---------|-------------|
| `BadUserInputException` | The caller asked for something illegal | GraphQL `BAD_USER_INPUT` / HTTP 400 |
| `EntityNotFoundException` | The referenced row does not exist | GraphQL `NOT_FOUND` / HTTP 404 |
| `DomainException` | Base type; carries a `DomainErrorCode` | classified by the advice |
| Provider exceptions (`SigniusIntegrationException`, `PostIdentIntegrationException`, …) | An external system failed | HTTP 502 / provider-specific classification |

Rules:

- **Do not use `IllegalArgumentException`, `IllegalStateException` or `RuntimeException`
  for business semantics.** They map to 500 and tell the caller nothing.
- An illegal lifecycle transition is `BadUserInputException` with a message naming the
  case, the attempted event and the current status. `KycCaseDecisionService` is the
  reference for the message shape.
- A provider failure is never re-thrown raw. Wrap it.
- Error messages MUST NOT contain personal data, provider payloads, or secrets.

## 4.2 Domain error vs technical error

- **Domain error** — expected, the caller can act on it, mapped to a 4xx / typed GraphQL
  error, not alerted on.
- **Technical error** — unexpected, mapped to 5xx, logged with a correlation id, alerted on.

Classifying a technical failure as a domain error to keep dashboards clean is forbidden.

---

# 5. Events

RMS has two distinct kinds. Do not conflate them.

## 5.1 Audit events

CloudEvents emitted through `common.audit` for regulated actions. Contract, retained,
catalogued (`docs/audit-kernel.md`, `docs/kyc/kyc-event-catalogue.md`).

- Past tense, business vocabulary.
- Attributed to an operator or to `svc:risk-management-service`.
- Personal data hashed or omitted.
- **Adding or changing an audit event is a contract change** and requires the catalogue
  to be updated in the same increment.

## 5.2 Spring application events

In-process, e.g. `qes/config/StatusChangeListener`. Internal coordination only.

- Never a substitute for an audit event.
- Never the mechanism for something that must survive a crash — that is an outbox row.
- A listener that performs business logic belongs in a service the publisher calls
  directly, unless the decoupling is deliberate and specified.

---

# 6. Anti-patterns

- A status held as a `String`
- A transition legality check duplicated in three services instead of the state machine
- An entity method that loads another row
- `@Transactional` on a self-invoked method
- A mapper with a repository dependency
- A provider enum used directly as our enum
- An `else ->` branch hiding an unmapped provider status
- Personal data in an exception message, a log line, or an unhashed audit field
- A second service writing a lifecycle column it does not own
- An outbox row written outside the business transaction that justified it
