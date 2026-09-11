# CLAUDE.md – JRL Contract Management

## Project

JRL Contract Management is a reference-grade Contract Management backend for
bicycle salary-sacrifice leasing.
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
3. [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – Kotlin coding style, naming conventions, class roles
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

## Ubiquitous Language

The domain is German bicycle leasing. The German terms are the **real** names of
these concepts, and the abbreviations are what the business uses in conversation
and in every upstream system. They are part of the model, not decoration.

| English (code) | German | Abbreviation |
|----------------|--------|--------------|
| Master Leasing Contract | Leasing-Rahmenvertrag | **LRV** / MLC |
| Individual Leasing Contract | Einzel-Leasingvertrag | **ELV** / ILC |
| Service Agreement | Dienstleistungsvertrag | **DLV** / SA |
| Usage Provision Contract | Nutzungsüberlassungsvertrag | **ÜV** / UEV |
| Employer | Arbeitgeber | AG |
| Lessor | Leasinggeber | LG |
| Employee leasing a bike | JobRadler:in | — |
| Cancellation reason | Kündigungsgrund | — |
| Notice period | Kündigungsfrist | — |
| Joint liability across a corporate group | gesamtschuldnerische Haftung | KUV / gsH |
| Return quota | Rückgabekontingent | — |
| Application / sales-order number | Antragsnummer | KAU |

**Code identifiers are English; specs name the German term at least once per
concept.** The split is deliberate: a Kotlin codebase in which
`kuendigungsgrund` sits beside `creditLimit` is worse than either language used
consistently, but a spec that never says *Kündigungsgrund* leaves the reader
unable to talk to the business or to match a field against Odoo or Radar.

Source of the model: the *Contract Management Domain Data model* page in the
Confluence space `JCM`.

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
invoked as `/loop-uc UC05`:

Read the use case's `## 10. Definition of Done` → pick one unmet criterion →
run the inner loop → re-evaluate → repeat. The loop exits only when every DoD
box is ticked, the drift review returns `PASS`, and the quality gates are green.

**No implementation without a spec. No architectural change without an ADR.
No production code without a failing test.**

### Always-Read Documents

The following documents apply to **every** implementation task and MUST be read
before writing any code, regardless of the task scope:

- [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – package structure, layering rules, dependency directions, registered bounded contexts
- [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – naming conventions, class roles (`*GraphQLController`, `*Driver`, `*Entity`, …), visibility rules
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
| [`spec-documenter`](.claude/agents/spec-documenter.md) | fable | docs only, never `src/**` | after every GREEN step | reconciled specs, `graphql/*.graphql`, DoD scoreboard |

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

## GraphQL Operation Documentation

GraphQL is the **only** inbound adapter in this system
(`adr/0008-graphql-only-inbound-adapter.adr.md`). There is no REST controller,
no `rest/` folder and no springdoc.

Every implemented GraphQL operation **must** have a corresponding request file in
`graphql/`.

- One file per use case, named `uc<nn>-<use-case-name>.graphql`
- Each file must cover: the happy-path operation, and one operation per documented
  error case — GraphQL answers `200 OK` with an `errors` array, so the error case
  is identified by the `extensions.classification` the resolver sets, not by a
  status code
- Files are updated as part of the **Implement Phase** — not as an afterthought.
  `spec-documenter` verifies this on every increment.
- The `graphql/` folder is the living contract between the backend and any GraphQL
  client (GraphiQL, Postman, Insomnia, curl). The **schema** under
  `src/main/resources/graphql/` is the type contract; these files are the
  *worked examples* of exercising it, including the failures.
