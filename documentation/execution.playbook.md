# Execution Playbook (Agents)

## Purpose

This Playbook makes SDD executable:
It describes how Agents MUST plan, create specs, implement, verify and closes tasks.


# 1. Operating Mode

This playbook defines the **inner loop**: one increment, start to finish.
The outer loop that repeats it until a use case is done is `loop.playbook.md`.

Agents operate strictly in the following order:

1. Locate specs
2. Determine target scope
3. Produce plan (tasks + acceptance criteria)
4. Create necessary ADRs and wait for user confirmation
5. **Write the failing test and prove it fails** (RED)
6. **Implement the smallest code that passes it** (GREEN)
7. **Refactor with the suite green** (REFACTOR)
8. Dispatch drift review and documentation in parallel
9. Run quality gates
10. Provide completion report

Steps 5–7 are non-negotiable and non-reorderable; see `tdd.definition.md`.
Production code written before step 5 has produced quoted evidence is a
protocol violation, not a shortcut.

No shortcuts.

# 2. Input Documents (Source of Truth Order)

The ranked authority order lives in `CLAUDE.md`.
See `file-usage.definition.md` for what each document is responsible for.

In case of ambiguity, the higher hierarchy takes precedence.


# 3. Execution Loop (Per Task)

## 3.1 Read Phase

Goal: Understand domain impact before touching code.

### Always-Read Checklist (every task, no exceptions)

Before anything else, read these four documents:

- [ ] `documentation/architecture.definition.md` – package structure, layering rules, dependency directions, registered bounded contexts (§ 11)
- [ ] `documentation/coding-style.definition.md` – naming conventions, class roles (`*RestAPI`, `*Controller`, `*Driver`, …), visibility rules
- [ ] `documentation/modelling.definition.md` – Always-Valid doctrine, DDD building blocks
- [ ] `documentation/tdd.definition.md` – the RED-first rule and its evidence requirement

These define conventions that apply to every class written. Missing them causes implementation to violate established patterns.

### Task-Specific Analysis

Agent MUST then:
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
- Definition of Done (§ 10 of the spec) — every `AC-NN` cited by at least one
  objectively checkable item


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

**Trigger list: `sdd.playbook.md` § 6.** It is not restated here.

Check that list before every increment. If any trigger fires, the ADR is written
and confirmed **before** implementation starts — including before the RED test,
because the decision shapes what the test asserts.

Implementation waits for ADR confirmation. An agent that hits a trigger
mid-increment halts and asks; it does not decide.

## 3.3 Plan Phase

Agent provides:
- Task list (max 5–8 steps)
- Risks
- Acceptance Criteria as identified `AC-NN` blocks (Given / When / Then) — see `sdd.playbook.md` § 4.1
- Affected files (precise paths)
- Required test classes, and which `AC-NN` each one covers

Acceptance Criteria must be domain-oriented, not technical.

## 3.4 Implement Phase (TDD)

Governed by `tdd.definition.md`. One DoD criterion per pass through 3.4.1–3.4.3.

Standing rules for the whole phase:
- Smallest safe increment
- No refactoring outside scope
- No layering violations
- Domain logic only inside domain package
- No business logic inside controllers or adapters
- Always-Valid domain model enforcement

Technical constraints:
- Java 25 features allowed (records, sealed types, pattern matching, flexible
  constructor bodies, unnamed variables, stream gatherers) — matches the toolchain
  declared in `gradle/libs.versions.toml`; raising the baseline is an ADR trigger
  (`sdd.playbook.md` § 6 item 13, `adr/0006-java-25-baseline.adr.md`)
- **No preview features.** `--enable-preview` is not enabled and enabling it is a
  separate ADR — it changes the artifact's compatibility guarantees
- No field injection
- Constructor injection only
- No framework types inside domain layer

### 3.4.1 RED

Write **one** test for the selected criterion, at the layer `tdd.definition.md`
§ 3 prescribes. Run it. Watch it fail.

Exit condition: the test fails for the intended reason, and the agent has
**quoted the actual failure output** — command, test name, assertion message.

A predicted failure is not a failure. No quoted RED, no GREEN.

### 3.4.2 GREEN

Write the least production code that makes that one test pass.

Exit condition: the new test passes and no previously passing test broke.

Do not implement criteria that are not currently red.

### 3.4.3 REFACTOR

Improve naming, extract value objects, remove duplication, relocate logic to
where the ontology says it belongs.

Exit condition: the full suite is green and **no test file was modified**.
If a test had to change, the behaviour changed — back it out and do it as its
own RED.

## 3.5 Review & Document Phase (Mandatory)

After REFACTOR, dispatch both subagents **in parallel** on the working diff:

Reach the review through **`/code-review --increment`**, which fans out to all four axes
in one message. It is one entry point rather than four, so no axis is silently skipped, and
it deduplicates a defect that surfaces on more than one.

| Axis | Owner | Authority |
|------|-------|-----------|
| architecture drift | `ddd-hex-reviewer` | **blocks** |
| conformance to the spec | `conformance-reviewer` | **blocks** |
| logical correctness | the review skill's logic axis | reported |
| security | the review skill's security axis | reported |

Dispatched alongside it, not through it:

| Agent | Returns |
|-------|---------|
| `spec-documenter` | reconciled specs, the files in `api/`, DoD scoreboard |

`spec-documenter` is deliberately outside the review skill: it **writes**, and the review
skill writes nothing. Keeping the reviewer read-only is what stops it becoming the acceptor
of its own changes.

Rules:

- **A `DRIFT` or `UNMET` verdict blocks the increment.** The finding is fixed through a new
  RED → GREEN → REFACTOR cycle, not by editing the reviewer's checklist and not by arguing
  with it in the report.
- Drift is never traded away for progress, and a DoD box is never ticked while a `DRIFT` or
  `UNMET` finding touches it.
- **Logic and security findings do not block, and are not optional either.** Each is fixed
  or explicitly accepted with a reason before the increment closes. They report rather than
  block because they are model judgement, where false positives are ordinary — and a
  blocking gate that cries wolf gets switched off entirely, taking the two reliable axes
  with it (`test.definition.md` § 7, gate 12).
- `spec-documenter` never edits `src/**`. Where the code contradicts a spec
  it reports the contradiction; the agent driving the increment decides which
  side is wrong.
- Documentation is part of the increment, not a follow-up. An increment whose
  specs still describe the previous state is unfinished.

## 3.6 Verify Phase (Mandatory)

**Quality gates: `test.definition.md` § 7.** They are not restated here.

Commands (`technical.spec.md`, Build & Quality Gates):
```shell
./gradlew clean test
./gradlew build
```

Test rules, taxonomy, and coverage expectations are owned by
`test.definition.md` — this playbook does not redefine them
(`file-usage.definition.md` § 4).

No task is complete without automated verification.

## 3.7 Closeout Phase

Agent MUST provide:
- What changed?
- Which specs were updated?
- Which ADRs were created/modified?
- How was it verified — **including the quoted RED failure for each criterion**
  (`tdd.definition.md` § 2)?
- Drift review verdict
- Open risks or follow-ups

No silent assumptions.


# 4. Manual Verification Checklist (Lightweight)

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

# 5. Agent Output Contract

Model-agnostic. Every agent output MUST end with these five labelled sections,
in this order:

```
Completed      – what was implemented, one line per item
Verification   – commands run + status, and the quoted RED failure per criterion
Specs touched  – specs and ADRs created or updated
Drift review   – PASS, or DRIFT with the findings
Next step      – max 1–3 bullets
```

Rules:

- `Verification` without a quoted RED failure fails the contract, even if every
  gate is green (`tdd.definition.md` § 2).
- `Drift review` is never omitted and never self-assessed. It carries the
  `ddd-hex-reviewer` verdict verbatim.
- Plain-text labels, not emoji. The contract must read identically in a
  terminal, a diff, and a PR body.

No verbose summaries.
No emotional commentary.
Structured and precise.

---

# 6. Anti-Patterns (Strictly Forbidden)

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
- Writing production code before a quoted RED failure exists
- Weakening or disabling a test to reach green
- Ticking a DoD box that no named passing test backs
- Overruling a `DRIFT` verdict instead of fixing the finding

TDD-specific anti-patterns are enumerated in `tdd.definition.md` § 5.

Spec-first. Domain-centric. Test-driven. Controlled increments.