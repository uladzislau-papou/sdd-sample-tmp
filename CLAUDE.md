# CLAUDE.md

## Project

A spec-driven service template, carrying a small working Tour Booking example that proves
the template compiles and its gates fire. See [`README.md`](README.md) for what the
repository is, and
[`documentation/project.definition.md`](documentation/project.definition.md) for the
example's vision and — importantly — its non-goals.

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

Three lists are **single-sourced** and must not be copied anywhere else:

| List | Canonical location | Enforced by |
|------|--------------------|-------------|
| ADR triggers | [`sdd.playbook.md`](documentation/sdd.playbook.md) § 6 | review |
| Quality gates (merge blockers) | [`test.definition.md`](documentation/test.definition.md) § 7 | review |
| Registered bounded contexts | [`architecture.definition.md`](documentation/architecture.definition.md) § 11 | **`ContextRegistryTest` parses it** |

The third was added because it was already being copied — as prose in the document and as a
string literal in a test — so adding a context meant editing both, and the test would have
kept passing against the stale list. It is now parsed, and § 11 carries a written format
contract stating the shape the parser depends on. ADR-0014 records the generalisation: when
a rule rots, give it an executable owner rather than restating it.

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

Four subagents enforce what prose cannot. All are defined in `.claude/agents/`.

| Agent | Model | Access | Fires when | Produces |
|-------|-------|--------|-----------|----------|
| [`ddd-hex-reviewer`](.claude/agents/ddd-hex-reviewer.md) | opus | read-only | after every GREEN step | `PASS` or `DRIFT` + `file:line` findings |
| [`conformance-reviewer`](.claude/agents/conformance-reviewer.md) | sonnet | read + `Bash` to run tests | after every GREEN step | `PASS` or `UNMET` — § 7 criteria matched to tests, § 10 boxes to artifacts |
| [`spec-documenter`](.claude/agents/spec-documenter.md) | fable | docs only, never `app/src/**` | after every GREEN step | reconciled specs, the files in `api/`, DoD scoreboard |
| [`spec-reviewer`](.claude/agents/spec-reviewer.md) | opus | read-only | after a spec is drafted from a ticket | `PASS` or `GAPS` — hunts claims with no source |

Rules:

- A reviewer **reports**; it never edits. `ddd-hex-reviewer` and `conformance-reviewer` both
  block the increment — drift and unmet criteria are never traded away for progress.
- `spec-documenter` edits documentation only. Where code contradicts a spec it reports the
  contradiction upward rather than rewriting either side.
- No agent may weaken a test or mask a failing build. `spec-documenter` and `spec-reviewer`
  have no `Bash` for exactly this reason; `conformance-reviewer` has it only to run tests,
  because a verdict built on predicting a test result is a guess wearing a verdict's
  clothes.

**Why two spec reviewers and not one with a mode flag.** `spec-reviewer` compares a spec to
its **sources** and hunts *invention*. `conformance-reviewer` compares **code** to a spec
and hunts *omission*. The two failure modes leave opposite traces: invention leaves a claim
in the text, which is something to read; omission leaves nothing, and is visible only by
walking a list and asking what each item points at. One agent holding both instructions
would need every rule qualified by mode, and the qualification is where precision goes.

### Slash commands

| Command | Purpose |
|---------|---------|
| `/spec-create <TICKET>` | Jira/Confluence ticket → use-case spec(s), decomposition confirmed first |
| `/uc-to-plan <ucNN>` | use case spec → `plan.md` |
| `/plan-to-task` | `plan.md` → phased checkbox `tasks.md` |
| `/execute-task <N.M>` | implement one task block, TDD-first |
| `/loop-uc <UCNN>` | run the outer loop until the use case's DoD is met |
| `/code-review [--increment\|--pr N\|--branch B]` | four-axis review with split authority |

`/code-review` deliberately **shadows the built-in skill of the same name**. The consequence
is accepted: its logic axis runs through `mattpocock-skills:code-review`, which keeps its own
namespace. The built-in stays available to a human invoking it directly.

---------|---------|
| `/uc-to-plan <ucNN>` | use case spec → `plan.md` |
| `/plan-to-task` | `plan.md` → phased checkbox `tasks.md` |
| `/execute-task <N.M>` | implement one task block, TDD-first |
| `/loop-uc <UCNN>` | run the outer loop until the use case's DoD is met |

---

## API Contract Documentation

Every use case exposed over a transport **must** have a matching executable request file in
`api/`.

- `api/uc<nn>-<use-case-name>.http` for REST
- `api/uc<nn>-<use-case-name>.graphql` for GraphQL
- One file **per transport the use case actually uses**, and **none** for a use case with no
  external API — a listener-driven use case says so in its § 9. UC06 is the worked example.
- Each file covers the happy path plus one request per documented status or error
  classification.
- Files are written in the **Implement Phase**, not afterwards. `spec-documenter` verifies
  them on every increment.

The folder is `api/`, not `rest/`: the stack carries two transports, and a folder named
after one of them left the other's contract nowhere to live (ADR-0012).

These files are the living contract between the backend and any client. They are also the
cheapest available check that a spec's § 9 and the code still agree, which is why they are
verified rather than treated as documentation.
