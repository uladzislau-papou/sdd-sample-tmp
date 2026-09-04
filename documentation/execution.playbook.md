# Execution Playbook (Agents)

## Purpose

This playbook makes SDD executable: how agents plan, spec, implement, verify and close a
task.

---

# 1. Operating Mode

This playbook defines the **inner loop**: one increment, start to finish. The outer loop
that repeats it until a use case is done is `loop.playbook.md`.

Agents operate strictly in this order:

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

Steps 5–7 are non-negotiable and non-reorderable; see `tdd.definition.md`. Production code
written before step 5 has produced quoted evidence is a protocol violation, not a shortcut.

---

# 2. Input Documents

The ranked authority order lives in `CLAUDE.md`. See `file-usage.definition.md` for what
each document is responsible for. In case of ambiguity, the higher authority wins.

---

# 3. Execution Loop (Per Task)

## 3.1 Read Phase

Goal: understand module and contract impact before touching code.

### Always-Read Checklist (every task, no exceptions)

- [ ] `documentation/architecture.definition.md` — package structure, layering, dependency
      rules, § 11 registry of modules / cross-module edges / warts
- [ ] `documentation/coding-style.definition.md` — naming, class roles, KDoc, error style
- [ ] `documentation/modelling.definition.md` — entities-as-domain-model, where validation
      lives, error taxonomy
- [ ] `documentation/tdd.definition.md` — the RED-first rule and its evidence requirement

### Task-Specific Analysis

Identify the impacted:

- Module(s) — and whether the change adds a cross-module edge (§ 11.2)
- Entities read and entities **written**
- Lifecycle transitions affected, and whether the state machine configuration changes
- Service(s) that own the transaction
- Inbound surface(s): GraphQL operation, REST endpoint, webhook, scheduler
- Outbound integration(s): provider client, outbox delivery
- Scopes required

Then check:

- Does the database schema change? → Flyway migration, timestamp-named
- Does the GraphQL schema change? → `.graphqls` and controller together
- Does an audit event change? → catalogue update in `docs/`
- Does configuration change? → `.env.example`
- Does a provider contract change? → integration spec first
- Do any ADRs conflict with, or constrain, this change?

No implementation before full scope clarity.

## 3.2 Spec Phase

### 3.2.1 Use case spec

Must define: intent · input contract · output contract (including the error-classification
table) · preconditions · flow · side effects (persistence, audit events, outbound calls) ·
acceptance criteria as `AC-NN` · failure scenarios · API contract with required scope ·
Definition of Done.

### 3.2.2 Domain spec (if the persistent model changes)

Entity, its columns and their meaning, lifecycle states and legal transitions, who owns
writes to each lifecycle column, optimistic-locking requirement, emitted audit events.

### 3.2.3 Integration spec (if a surface or provider contract changes)

Responsibility · the operation contract · the translation table between the external
vocabulary and ours · error mapping · idempotency and retry expectations · timeouts ·
configuration keys.

### 3.2.4 ADR (if an architectural decision is required)

**Trigger list: `sdd.playbook.md` § 6.** Not restated here.

Check that list before every increment. If any trigger fires, the ADR is written and
confirmed **before** implementation starts — including before the RED test, because the
decision shapes what the test asserts.

An agent that hits a trigger mid-increment halts and asks; it does not decide.

## 3.3 Plan Phase

- Task list (5–8 steps)
- Risks — for RMS, name explicitly: provider-contract risk, migration risk on populated
  tables, concurrency/optimistic-lock risk, audit-contract risk, personal-data exposure
- Acceptance Criteria as identified `AC-NN` blocks
- Affected files (precise paths, in the service repository)
- Required test classes, and which `AC-NN` each covers

Acceptance Criteria are business-oriented, not technical.

## 3.4 Implement Phase (TDD)

Governed by `tdd.definition.md`. One DoD criterion per pass through 3.4.1–3.4.3.

Standing rules:

- Smallest safe increment
- No refactoring outside scope
- No layering violations (`architecture.definition.md` § 6)
- Business logic only in services
- No business logic in controllers, mappers or advices
- Constructor injection only
- Provider vocabulary stays in `api/integration` and its mappers

Technical constraints (`technical.spec.md`):

- Kotlin 2.3.0, JVM 17, Spring Boot 4.0.3; `allWarningsAsErrors` is on
- Raising any baseline is an ADR trigger
- New dependency → ADR
- Schema change → new timestamp-named Flyway migration; never edit an applied one

### 3.4.1 RED

Write **one** test for the selected criterion, at the layer `tdd.definition.md` § 3
prescribes. Run it. Watch it fail.

```shell
./gradlew test --tests '*<TestClass>.<method>'
```

Exit condition: the test fails for the intended reason and the agent has **quoted the
actual failure output**. A predicted failure is not a failure. No quoted RED, no GREEN.

If the test unexpectedly passes, apply `tdd.definition.md` § 2.2 — that is common in this
brownfield codebase and is a finding, not a formality to skip.

### 3.4.2 GREEN

Least production code that makes that one test pass. Do not implement criteria that are not
currently red.

### 3.4.3 REFACTOR

Improve naming, extract private steps, remove duplication, relocate logic. Exit condition:
full suite green and **no test file modified**.

## 3.5 Review & Document Phase (Mandatory)

After REFACTOR, dispatch both subagents **in parallel** on the working diff:

| Agent | Returns |
|-------|---------|
| `rms-architecture-reviewer` | `PASS` or `DRIFT` + `file:line` findings |
| `spec-documenter` | reconciled specs, `rest/*.http`, DoD scoreboard |

Rules:

- **A `DRIFT` verdict blocks the increment.** The finding is fixed through a new
  RED → GREEN → REFACTOR cycle, not by editing the reviewer's checklist and not by arguing
  with it in the report.
- Drift is never traded away for progress, and a DoD box is never ticked while a `DRIFT`
  finding touches it.
- `spec-documenter` never edits `src/**`. Where the code contradicts a spec it reports the
  contradiction; the agent driving the increment decides which side is wrong.
- Documentation is part of the increment, not a follow-up.

## 3.6 Verify Phase (Mandatory)

**Quality gates: `test.definition.md` § 7.** Not restated here. Commands:

```shell
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew detekt
./gradlew test
./gradlew build
```

Always `spotlessApply` before `detekt`.

Run from the **service** repository. This is a single-module build — no `:app:` prefix.

No task is complete without automated verification.

## 3.7 Closeout Phase

Provide:

- What changed?
- Which specs were updated?
- Which ADRs were created/modified?
- How was it verified — **including the quoted RED failure for each criterion**?
- Drift review verdict
- Open risks or follow-ups

No silent assumptions.

---

# 4. Manual Verification Checklist (Lightweight)

When applicable:

- GraphQL errors carry the right classification; REST returns the right status
- Validation messages are meaningful and free of personal data
- Missing token → 401; wrong scope → 403
- Transition legality holds from every state the contract names
- Provider failure surfaces as a typed error, not a 500
- The audit event fired, with the right actor
- A retried delivery does not double-apply
- No stack trace or provider payload leaked in a response
- `.env.example` covers every new variable
- `./gradlew build` produces the artifact

---

# 5. Agent Output Contract

Every agent output MUST end with these five labelled sections, in this order:

```
Completed      – what was implemented, one line per item
Verification   – commands run + status, and the quoted RED failure per criterion
Specs touched  – specs and ADRs created or updated
Drift review   – PASS, or DRIFT with the findings
Next step      – max 1–3 bullets
```

Rules:

- `Verification` without a quoted RED failure fails the contract, even if every gate is
  green (`tdd.definition.md` § 2).
- `Drift review` is never omitted and never self-assessed. It carries the
  `rms-architecture-reviewer` verdict verbatim.
- Plain-text labels, not emoji. The contract must read identically in a terminal, a diff
  and a PR body.

No verbose summaries. No emotional commentary. Structured and precise.

---

# 6. Anti-Patterns (Strictly Forbidden)

- Business logic in controllers, mappers or advices
- A repository injected into a controller
- An entity returned from a controller
- A provider model in a service signature or DTO
- Skipping spec updates
- Introducing a library without an ADR
- Editing an applied Flyway migration
- Adding a sequential `V<n>__` migration
- A new endpoint with no scope and no authorization test
- Partial implementation without tests
- Ignoring or disabling a failing test
- `@Suppress` to get past `allWarningsAsErrors` or detekt
- Modifying unrelated modules "while here"
- Writing production code before a quoted RED failure exists
- Ticking a DoD box that no named passing test backs
- Overruling a `DRIFT` verdict instead of fixing the finding

TDD-specific anti-patterns are enumerated in `tdd.definition.md` § 5.

Spec-first. Service-centric. Test-driven. Controlled increments.
