# CLAUDE.md – Risk Management Service

## Project

Risk Management Service (RMS) is the unified identity, signature and risk backend:
identification and QES via IDnow / PostIdent / Signius, KYC case management via KYCnow,
and onboarding/credit integration with RADar.

See [`documentation/project.definition.md`](documentation/project.definition.md) for
vision, purpose and non-goals.

---

## Documentation Authority Order

This list is **canonical**. It is the single ranked authority order for the project; no
other document restates it.
[`documentation/file-usage.definition.md`](documentation/file-usage.definition.md)
defines what each file is *responsible for* and references this order.

Rules are defined in the following files, ordered by precedence (highest first):

1. [`documentation/project.definition.md`](documentation/project.definition.md) – Vision, strategic intent, non-goals
2. [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – Layering, package ontology, dependency rules, **registered modules, cross-module edges and warts** (§ 11)
3. [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – Kotlin style, naming, class roles, KDoc
4. [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – Entities-as-domain-model doctrine, validity, error taxonomy, events
5. [`documentation/technical.spec.md`](documentation/technical.spec.md) – Tech stack, API surface, persistence, migrations, tooling
6. [`documentation/test.definition.md`](documentation/test.definition.md) – Test taxonomy, assertion rules, **canonical quality gates** (§ 7)
7. [`documentation/tdd.definition.md`](documentation/tdd.definition.md) – RED → GREEN → REFACTOR, test-first ordering, RED evidence rule
8. [`documentation/sdd.playbook.md`](documentation/sdd.playbook.md) – SDD governance, **canonical ADR triggers** (§ 6)
9. [`documentation/execution.playbook.md`](documentation/execution.playbook.md) – Inner loop: per-task execution phases, agent output contract
10. [`documentation/loop.playbook.md`](documentation/loop.playbook.md) – Outer loop: iterate a use case until its Definition of Done is met
11. [`documentation/layer-responsibilities.definition.md`](documentation/layer-responsibilities.definition.md) – Controller / service / entity boundary clarification
12. [`documentation/file-naming.definition.md`](documentation/file-naming.definition.md) – Document filename conventions
13. [`documentation/adr/`](documentation/adr/) – Architectural Decision Records
14. [`documentation/domain/`](documentation/domain/) – Concrete domain specs
15. [`documentation/integrations/`](documentation/integrations/) – Concrete inbound-surface and outbound-integration specs
16. [`documentation/use-cases/`](documentation/use-cases/) – Concrete use case specs, each carrying its Definition of Done
17. [`documentation/notes.md`](documentation/notes.md) – Scratchpad (non-authoritative)

Higher documents override lower ones. Templates do not override definitions.

Three lists are **single-sourced** and must not be copied anywhere else:

| List | Canonical location |
|------|--------------------|
| ADR triggers | [`sdd.playbook.md`](documentation/sdd.playbook.md) § 6 |
| Quality gates (merge blockers) | [`test.definition.md`](documentation/test.definition.md) § 7 |
| Registered modules, cross-module edges, warts | [`architecture.definition.md`](documentation/architecture.definition.md) § 11 |

### Relationship to the service repository's own docs

The service repository carries `README.md`, `CONTRIBUTING.md` and `docs/`. Those are the
**operational** reference: environment variables, local setup, token generation, the
audit event catalogue. This `documentation/` tree is the **governance** reference.

Where they overlap, the README wins on *how to run it* and this tree wins on *what is
allowed*. The one exception is `architecture.definition.md` § 2.2, which explicitly
overrides the README's claim about Ports & Adapters.

---

## How to Work on This Project

Two nested loops. The **inner loop** implements one increment; the **outer loop** repeats
the inner loop until a use case is done.

### Inner loop — per task

Defined in [`documentation/execution.playbook.md`](documentation/execution.playbook.md):

1. **Read Phase** – understand module and contract impact
2. **Spec Phase** – create/update specs and ADRs as required
3. **Plan Phase** – task list, risks, acceptance criteria
4. **Implement Phase (TDD)** – RED (failing test first) → GREEN (minimal code) → REFACTOR
5. **Review & Document Phase** – dispatch `rms-architecture-reviewer` and `spec-documenter` in parallel
6. **Verify Phase** – the quality gates in `test.definition.md` § 7
7. **Closeout Phase** – structured completion report

### Outer loop — per use case

Defined in [`documentation/loop.playbook.md`](documentation/loop.playbook.md), invoked as
`/loop-uc UC07`:

Read the use case's `## 10. Definition of Done` → pick one unmet criterion → run the inner
loop → re-evaluate → repeat. The loop exits only when every DoD box is ticked, the drift
review returns `PASS`, and the quality gates are green.

**No implementation without a spec. No architectural change without an ADR.
No production code without a failing test.**

### Always-Read Documents

These apply to **every** implementation task and MUST be read before writing any code,
regardless of scope:

- [`documentation/architecture.definition.md`](documentation/architecture.definition.md) – package structure, layering, dependency rules, § 11 registry
- [`documentation/coding-style.definition.md`](documentation/coding-style.definition.md) – naming, class roles, KDoc, error style
- [`documentation/modelling.definition.md`](documentation/modelling.definition.md) – entities-as-domain-model, where validation lives, error taxonomy
- [`documentation/tdd.definition.md`](documentation/tdd.definition.md) – the RED-first rule and its evidence requirement

---

## Non-negotiables

Short list of the things most often got wrong in this codebase. Each is expanded in the
document cited.

- **Never edit an applied Flyway migration.** New timestamp-named file
  (`technical.spec.md` § 5).
- **Never add a sequential `V<n>__` migration.** Timestamps only.
- **A controller never injects a repository, never opens a transaction, never returns an
  entity** (`architecture.definition.md` § 6).
- **A provider model never leaves `api/integration`.** Translate in a mapper
  (`architecture.definition.md` § 4.6).
- **`@Transactional` does not apply to self-invoked methods.** Split the class
  (`modelling.definition.md` § 2.5).
- **A `when` over an enum is exhaustive without `else`** (`coding-style.definition.md` § 2.3).
- **The acting operator comes from `OperatorContext`, never from request input**
  (`architecture.definition.md` § 8.2).
- **No personal data in logs, exception messages, or unhashed audit payloads.**
- **`allWarningsAsErrors` is on.** A warning fails the build; do not `@Suppress` to green.
- **New config variable → `.env.example` in the same increment.**
- **New GraphQL field → schema and controller together**, or the contract is broken.

---

## Agent Roster

Two subagents enforce what prose cannot. Both are defined in `.claude/agents/` and are
dispatched **in parallel** at the Review & Document Phase of every increment.

| Agent | Model | Access | Fires when | Produces |
|-------|-------|--------|-----------|----------|
| [`rms-architecture-reviewer`](.claude/agents/rms-architecture-reviewer.md) | opus | read-only | after every GREEN step | `PASS` or `DRIFT` + `file:line` findings |
| [`spec-documenter`](.claude/agents/spec-documenter.md) | fable | docs only, never `src/**` | after every GREEN step | reconciled specs, `rest/*.http`, DoD scoreboard |

Rules:

- `rms-architecture-reviewer` **reports**; it never edits. A `DRIFT` verdict blocks the
  increment — drift is never traded away for progress.
- `spec-documenter` edits documentation only. Where code contradicts a spec it reports the
  contradiction upward rather than rewriting either side.
- Neither agent may weaken a test or mask a failing build. `spec-documenter` has no `Bash`
  access for exactly this reason.

### Slash commands

| Command | Purpose |
|---------|---------|
| `/spec-create <ticket>` | Jira/Confluence ticket → one or more use case specs |
| `/uc-to-plan <ucNN>` | use case spec → `plan.md` |
| `/plan-to-task` | `plan.md` → phased checkbox `tasks.md` |
| `/execute-task <N.M>` | implement one task block, TDD-first |
| `/loop-uc <UCNN>` | run the outer loop until the use case's DoD is met |

---

## API Request Documentation

Every implemented API operation **must** have a corresponding JetBrains HTTP Client file
in `rest/`.

- One file per use case, named `uc<nn>-<use-case-name>.http`
- **GraphQL operations are POSTs to the module's `/graphql` endpoint** with the query in
  the JSON body — see `rest/README.md` for the shape
- Each file must cover: the happy-path request, and one request per documented error case
  (`BAD_USER_INPUT`, `NOT_FOUND`, 400, 403, 409, 502, …)
- Files are updated as part of the **Implement Phase** — not as an afterthought.
  `spec-documenter` verifies this on every increment.
- `rest/` is the living contract between the backend and any HTTP client (IntelliJ,
  Postman, curl). It complements, and does not replace, the Playwright suite in
  `playwright/api/`.