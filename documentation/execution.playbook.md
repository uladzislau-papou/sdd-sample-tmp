# Execution Playbook (Agents)

## Purpose

This Playbook makes SDD executable:
It describes how Agents MUST plan, create specs, implement, verify and closes tasks.


# 1. Operating Mode

Agents operate strictly in the following order:

1. Locate specs
2. Determine target scope
3. Produce plan (tasks + acceptance criteria)
4. Create necessary ADRs and wait for user confirmation
5. Implement smallest safe increment
6. Run quality gates
7. Update specs/docs if needed
8. Provide completion report

No shortcuts.

# 2. Input Documents (Source of Truth Order)

See Definition in `file-usage.definition.md` for details.


In case of ambiguity, the higher hierarchy takes precedence.


# 3. Execution Loop (Per Task)

## 3.1 Read Phase

3.1 Read Phase

Goal: Understand domain impact before touching code.

Agent MUST:
- Identify impacted:
  - Bounded Context(s)
  - Aggregate(s)
  - Entity / Value Object(s)
  - Use Case(s) (Application Services)
  - Inbound Port(s)
  - Outbound Port(s)
  - Adapter(s)
- Check whether a corresponding use-case.spec.md exists. If not → create it first
- Check:
  - Are domain invariants affected?
  - Is a new Domain Event required?
  - Does persistence schema change?
  - Is a public API contract affected?
  - Review relevant ADRs for conflicts or constraints

No implementation before full scope clarity.

## 3.2 Spec Phase

Before implementation, the following MUST exist or be updated.

### 3.2.1 Use Case Spec

If a use case is created or modified, the spec MUST define:
- Intent
- Input contract
- Output contract
- Domain invariants enforced
- Failure scenarios
- Side effects:
- Domain Events
- Persistence operations
- External calls


### 3.2.2 Domain Spec (if domain model changes)

For new or modified domain objects:
- Aggregate Root definition
- Invariants (Always-Valid guarantees)
- State transitions
- Emitted Domain Events
- Consistency boundary

No anemic domain models.


### 3.2.3 Port Specification (if integration changes)

For new inbound/outbound ports:
- Responsibility
- Method contracts
- Exception model
- Transactional expectations
- Idempotency expectations (if applicable)


### 3.2.4 ADR (if architectural decision required)

ADR MUST be created before implementation when:
- Changing layering rules
- Introducing new infrastructure
- Modifying persistence strategy
- Changing transaction boundaries
- Introducing messaging/event broker
- Changing package ontology

Implementation waits for ADR confirmation.

## 3.3 Plan Phase

Agent provides:
- Task list (max 5–8 steps)
- Risks
- Acceptance Criteria (Given / When / Then)
- Affected files (precise paths)
- Required test classes

Acceptance Criteria must be domain-oriented, not technical.

## 3.4 Implement Phase

Rules:
- Smallest safe increment
- No refactoring outside scope
- No layering violations
- Domain logic only inside domain package
- No business logic inside controllers or adapters
- Always-Valid domain model enforcement

Technical constraints:
- Java 21 features allowed (records, sealed types, etc.)
- No field injection
- Constructor injection only
- No framework types inside domain layer

## 3.5 Verify Phase (Mandatory)

Minimum quality gates:
```shell
./gradlew clean test
./gradlew build
```

Test Requirements
- AssertJ for all assertions
- No plain assertEquals
- No incomplete test bodies
- No TODO tests

Test coverage expectations:
- Use case level tests
- Domain behavior tests
- Edge case tests for invariants
- Failure scenario tests

If persistence adapter changed:
- Integration test using Spring Boot test slice

If REST controller changed:
- WebMvcTest or SpringBootTest validation

No task is complete without automated verification

## 3.6 Closeout Phase

Agent MUST provide:
- What changed?
- Which specs were updated?
- Which ADRs were created/modified?
- How was it verified?
- Open risks or follow-ups

No silent assumptions.


# 4. Decision Triggers (ADR Required)

ADR is mandatory when:
- Introducing new external dependency
- Changing transaction boundary
- Introducing async processing
- Adding messaging (Kafka, RabbitMQ, etc.)
- Changing persistence technology
- Modifying package structure or domain boundaries
- Introducing caching
- Changing API versioning strategy
- Cross-context interaction model changes

No implicit architectural decisions.


# 5. Manual Verification Checklist (Lightweight)

When applicable:
- REST endpoints return correct status codes
- Validation errors are meaningful
- Domain invariants enforced
- No stack traces leaked in API responses
- Transaction boundaries respected
- Idempotency preserved where required
- Logging does not expose sensitive data
- Build artifact generated successfully

---

# 6. Output Format (Junie Response Contract)

Every Agent output MUST end with:
- ✅ Completed items
- 🧪 Verification results (commands + status)
- 📄 Specs/ADRs touched
- 🔜 Suggested next step (max 1–3 bullets)

No verbose summaries.
No emotional commentary.
Structured and precise.

---

# 7. Anti-Patterns (Strictly Forbidden)

- Business logic in controllers
- Business logic in adapters
- Anemic domain models
- Direct repository calls from controller
- Skipping spec updates
- Introducing new libraries without ADR
- Bypassing domain invariants
- Partial implementation without tests
- Ignoring failing tests
- Modifying unrelated modules “while here”

Spec-first. Domain-centric. Controlled increments.