# CLAUDE.md – Alpine Booking

## Project

Alpine Booking is a reference-grade Tour Booking backend.
See [`documentation/project.definition.md`](documentation/project.definition.md) for vision, purpose, and non-goals.

---

## Documentation Authority Order

This list is **canonical**. It is the single ranked authority order for the
project; no other document restates it.
[`documentation/file-usage.definition.md`](documentation/file-usage.definition.md)
defines what each file is *responsible for* and references this order.

Rules are defined in the following files, ordered by precedence (highest first):

1. [`documentation/project.definition.md`](documentation/project.definition.md) – Vision, strategic intent, non-goals
2. [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – Layering, package ontology, Ports & Adapters rules, registered bounded contexts
3. [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – Java coding style, naming conventions, class roles
4. [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – DDD building blocks, Always-Valid doctrine
5. [`documentation/technical.spec.md`](documentation/technical.spec.md) – Tech stack, tooling, persistence strategy
6. [`documentation/test.definition.md`](documentation/test.definition.md) – Test taxonomy, assertion rules, **canonical quality gates**
7. [`documentation/tdd.definition.md`](documentation/tdd.definition.md) – RED → GREEN → REFACTOR cycle, test-first ordering, RED evidence rule
8. [`documentation/sdd.playbook.md`](documentation/sdd.playbook.md) – SDD governance, **canonical ADR triggers**
9. [`documentation/execution.playbook.md`](documentation/execution.playbook.md) – Inner loop: per-task execution phases, agent output contract
10. [`documentation/loop.playbook.md`](documentation/loop.playbook.md) – Outer loop: iterate a use case until its Definition of Done is met
11. [`documentation/domain-vs-use-case.definition.md`](documentation/domain-vs-use-case.definition.md) – Responsibility boundary clarification
12. [`documentation/file-naming.definition.md`](documentation/file-naming.definition.md) – Document filename conventions
13. [`documentation/adr/`](documentation/adr/) – Architectural Decision Records
14. [`documentation/domain/`](documentation/domain/) – Concrete domain specs
15. [`documentation/ports/`](documentation/ports/) – Concrete inbound/outbound port specs
16. [`documentation/use-cases/`](documentation/use-cases/) – Concrete use case specs, each carrying its Definition of Done
17. [`documentation/notes.md`](documentation/notes.md) – Scratchpad (non-authoritative)

Higher documents override lower ones. Templates do not override definitions.

Two lists are **single-sourced** and must not be copied anywhere else:

| List | Canonical location |
|------|--------------------|
| ADR triggers | [`sdd.playbook.md`](documentation/sdd.playbook.md) § 6 |
| Quality gates (merge blockers) | [`test.definition.md`](documentation/test.definition.md) § 7 |

---

## How to Work on This Project

Two nested loops. The **inner loop** implements one increment; the **outer loop**
repeats the inner loop until a use case is done.

### Inner loop — per task

Defined in [`documentation/execution.playbook.md`](documentation/execution.playbook.md):

1. **Read Phase** – understand domain impact
2. **Spec Phase** – create/update specs and ADRs as required
3. **Plan Phase** – task list, risks, acceptance criteria
4. **Implement Phase (TDD)** – RED (failing test first) → GREEN (minimal code) → REFACTOR
5. **Review & Document Phase** – dispatch `ddd-hex-reviewer` and `spec-documenter` in parallel
6. **Verify Phase** – the quality gates in `test.definition.md` § 7
7. **Closeout Phase** – structured completion report

### Outer loop — per use case

Defined in [`documentation/loop.playbook.md`](documentation/loop.playbook.md),
invoked as `/loop-uc UC07`:

Read the use case's `## 10. Definition of Done` → pick one unmet criterion →
run the inner loop → re-evaluate → repeat. The loop exits only when every DoD
box is ticked, the drift review returns `PASS`, and the quality gates are green.

**No implementation without a spec. No architectural change without an ADR.
No production code without a failing test.**

### Always-Read Documents

The following documents apply to **every** implementation task and MUST be read
before writing any code, regardless of the task scope:

- [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – package structure, layering rules, dependency directions, registered bounded contexts
- [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – naming conventions, class roles (`*RestAPI`, `*Controller`, `*Driver`, …), visibility rules
- [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – Always-Valid doctrine, DDD building blocks
- [`documentation/tdd.definition.md`](documentation/tdd.definition.md) – the RED-first rule and its evidence requirement

---

## Agent Roster

Two subagents enforce what prose cannot. Both are defined in `.claude/agents/`
and are dispatched **in parallel** at the Review & Document Phase of every
increment.

| Agent | Model | Access | Fires when | Produces |
|-------|-------|--------|-----------|----------|
| [`ddd-hex-reviewer`](.claude/agents/ddd-hex-reviewer.md) | opus | read-only | after every GREEN step | `PASS` or `DRIFT` + `file:line` findings |
| [`spec-documenter`](.claude/agents/spec-documenter.md) | fable | docs only, never `app/src/**` | after every GREEN step | reconciled specs, `rest/*.http`, DoD scoreboard |

Rules:

- `ddd-hex-reviewer` **reports**; it never edits. A `DRIFT` verdict blocks the
  increment — drift is never traded away for progress.
- `spec-documenter` edits documentation only. Where code contradicts a spec it
  reports the contradiction upward rather than rewriting either side.
- Neither agent may weaken a test or mask a failing build. `spec-documenter`
  has no `Bash` access for exactly this reason.

### Slash commands

| Command | Purpose |
|---------|---------|
| `/uc-to-plan <ucNN>` | use case spec → `plan.md` |
| `/plan-to-task` | `plan.md` → phased checkbox `tasks.md` |
| `/execute-task <N.M>` | implement one task block, TDD-first |
| `/loop-uc <UCNN>` | run the outer loop until the use case's DoD is met |

---

## REST Endpoint Documentation

Every implemented REST endpoint **must** have a corresponding JetBrains HTTP Client file in `rest/`.

- One file per use case, named `uc<nn>-<use-case-name>.http`
- Each file must cover: the happy-path request, and one request per documented error case (400, 409, 502, etc.)
- Files are updated as part of the **Implement Phase** — not as an afterthought.
  `spec-documenter` verifies this on every increment.
- The `rest/` folder is the living contract between the backend and any HTTP client (Postman, IntelliJ, curl)
