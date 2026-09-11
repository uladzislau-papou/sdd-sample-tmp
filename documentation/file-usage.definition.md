# File Usage Definition

## Purpose

This document defines the authoritative role of each file inside the `/documentation` directory.

It prevents rule duplication, circular governance, and specification drift.


# 1. Authority Order (Source of Truth)

**The ranked authority order lives in [`../CLAUDE.md`](../CLAUDE.md) and nowhere else.**

`CLAUDE.md` is loaded into every agent's context automatically, so it must be
self-contained — which makes it the only sensible home for the order.

This document defines what each file is **responsible for**. `CLAUDE.md` defines
which file **wins** when two disagree.

Higher documents override lower ones.

Templates do not override definitions.


# 2. File Responsibilities

## project.definition.md
Defines:
- Vision
- Strategic intent
- Non-goals
- Scope boundary (which entities of the data model are built)

No technical detail belongs here.


## architecture.definition.md
Defines:
- Layering
- Package ontology
- Ports & Adapters rules
- Framework boundary rules
- **Registered bounded contexts — canonical location** (§ 11)
- GraphQL error classification mapping (§ 4.5)

Does NOT define domain modeling semantics.


## modelling.definition.md
Defines:
- Aggregates
- Entities
- Value Objects
- Domain Events
- Always-Valid doctrine
- **The four identity categories — canonical location**

Does NOT define use case orchestration.


## technical.spec.md
Defines:
- Tech stack decisions
- Tooling constraints
- Database strategy
- Migration strategy
- SQL classification rules
- Column type choices for money, percentages and time

Does NOT define governance or workflow.


## test.definition.md
Defines:
- Test taxonomy
- Assertion rules
- Coverage expectations
- **Quality gates (merge blockers) — canonical location**

Does NOT redefine architectural layering.
Does NOT define *when* in the cycle a test is written — that is `tdd.definition.md`.


## tdd.definition.md
Defines:
- The RED → GREEN → REFACTOR cycle
- Test-first ordering across the layers
- The RED evidence requirement
- TDD anti-patterns

Refines `test.definition.md`; MUST NOT restate its taxonomy or assertion rules.
Answers *when* a test is written. `test.definition.md` answers *what* it asserts.


## sdd.playbook.md
Defines:
- Spec hierarchy
- Governance discipline
- **ADR triggers — canonical location**
- Quality gate principles (the gate *list* lives in `test.definition.md` § 7)

Does NOT describe step-by-step execution.


## execution.playbook.md
Defines:
- Operational execution loop (the **inner** loop — one increment)
- Task workflow
- Review & document dispatch
- Agent output contract

It MUST NOT redefine rules already defined in:
- technical.spec
- modelling.definition
- test.definition
- tdd.definition
- sdd.playbook (ADR triggers)

It may reference them.


## loop.playbook.md
Defines:
- The **outer** loop — repeat the inner loop until a use case's Definition of Done is met
- Loop exit conditions and hard stops
- Model routing across loop stages

It MUST NOT redefine the inner-loop phases; it composes them.


## domain-vs-use-case.definition.md
Clarifies responsibility boundaries.
Purely conceptual.
Does not introduce new rules.


## /domain/*.md
Concrete domain specifications.
Must follow `domain.spec.template.md`.


## /ports/*.{inport,outport}.spec.md
Concrete inbound/outbound port specifications.
One file per port: responsibility, method contracts, exception model,
transactional and idempotency expectations.


## /use-cases/*.md
Concrete use case specifications.
Must follow `use-case.spec.template.md`.

Each spec owns its `## 10. Definition of Done` — the **authoritative** exit
condition for the outer loop. `tasks.md` mirrors it as a working scoreboard;
the spec always wins.


## /adr/*.adr.md
Records architectural decisions.
Immutable once accepted.


## coding-style.definition.md
Defines how the actual code should look.
Does not introduce any architectural decisions or functionality.
Changes over time.


## notes.md
Scratchpad.
Non-authoritative.
May not contradict formal definitions.


# 3. Template Governance

Templates:
- `/domain/domain.spec.template.md`
- `/use-cases/use-case.spec.template.md`

Templates define structure, not rules.

Changing a template does not change governance.
Changing governance requires updating the relevant definition file.


# 4. No Duplication Rule

Rules MUST appear in exactly one authoritative file.

Execution playbook references rules.
SDD playbook defines governance.
Technical spec defines tooling.

Copy-paste duplication is forbidden.


# 5. Change Protocol

When modifying rules:

1. Identify the highest authoritative file.
2. Update that file.
3. Remove duplicated rules from lower documents.
4. Document the change in an ADR if architectural.
5. **Commit the doctrine change on its own, before any code that relies on it** (§ 5.1).

## 5.1 Doctrine Lands First

**A change to a `*.definition.md` or `*.playbook.md` lands in its own commit, before any
code that relies on it.**

Why this is a rule and not a preference: `tdd.definition.md` § 2 makes TDD auditable for
code — the quoted RED failure is the evidence. Nothing makes spec-before-code auditable
for *rules*. A doctrine change and the code it sanctions, arriving in one commit, are
indistinguishable from the code arriving first and the rule being written afterwards to
authorise it. Both produce an identical diff, so "this was spec-first" becomes an
unfalsifiable claim in a project whose entire premise is that it is not.

Separating the commits makes the ordering a fact in the history rather than an assertion
in a report.

Rules:

- Doctrine change → its own commit, whose message states which rule changed and why.
- Anything relying on it → a **later** commit. "Anything" includes **build
  configuration**: applying a plugin, adding a dependency or wiring a task counts as
  relying on the rule that mandates it, exactly as production code does. A gate added to
  `test.definition.md` § 7 and the tooling that enforces it are two commits, not one.
- Never amend a doctrine commit to accommodate code written after it. If the rule turns
  out to be wrong, change it in a new commit and say so.
- A doctrine commit that *loosens* a rule deserves particular scrutiny: tightening a rule
  cannot retroactively legalise existing code, but loosening one can.
- `ddd-hex-reviewer` verifies ordering with
  `git log --diff-filter=M -- documentation/`.

### Recorded violations

Kept deliberately. A rule that lists the times it was broken is more credible than one
that reads as though it never has been.

- **The adaptation commit(s).** This repository's documentation set and its build
  configuration arrived together, as one conversion of a prior reference
  implementation to this domain and this stack. Slicing that retroactively into
  doctrine-then-build would fabricate an ordering that did not happen. The rule
  applies from the first increment that implements a use case onward.

This entry is not corrected by rewriting history, and neither are future ones: the
ordering either happened or it did not, and a tidier log that says otherwise is
worse than an accurate one that admits a breach. Rewriting is permitted for exactly
one reason — repairing a commit that does not build — and when it happens, the
breach still gets an entry here.


# 6. Anti-Patterns

- Duplicating governance rules in multiple files
- Letting execution.playbook redefine architecture
- Letting templates introduce new constraints
- Treating notes.md as specification
- Encoding a rule in an agent prompt instead of in the definition file it belongs to

Clarity over convenience.


# 7. Executable Layer (`.claude/`)

`/documentation` states the rules. `.claude/` executes them. The executable
layer is **subordinate**: it may enforce a documented rule, never invent one.

| Path | Role |
|------|------|
| `.claude/agents/*.md` | Subagent definitions. Each agent's checklist MUST cite the definition file and section it enforces. |
| `.claude/skills/*/SKILL.md` | Multi-step workflows (e.g. the outer loop). Compose playbook phases; do not redefine them. |
| `.claude/commands/*.md` | Single-shot slash commands. |
| `.claude/settings.json` | Checked-in harness config: tool permissions. Reproducible for anyone cloning the repo. |
| `.claude/settings.local.json` | Personal overrides. Never authoritative. |

Rules:

- If an agent needs a rule that is not written down, the rule goes into the
  appropriate `.definition.md` **first**, and the agent references it.
  A checklist item with no upstream citation is a governance bug.
- Agents that review MUST NOT edit. Agents that document MUST NOT touch
  `src/**`. Enforced by their `tools:` frontmatter, not by good intentions.
- A hardcoded list inside an agent prompt is duplication under § 4. Machine-checkable
  facts belong in the documents — e.g. the registered bounded contexts live in
  `architecture.definition.md` § 11, and the agent reads them from there.


# 8. On the argumentative style of these documents

A rule in this repository is usually written with the reasoning that produced it,
and often with the alternative that was rejected. That is deliberate and it is worth
saying why, because it reads as verbose against a conventional style guide.

A bare rule can only be obeyed or violated. A rule with its reasoning can also be
**correctly set aside** when a case genuinely falls outside it, and — more
importantly — can be recognised as wrong when it is. In a project whose premise is
that documents govern code, a document nobody can argue with is a document nobody
can fix.

Two consequences:

- **When a rule is changed, change the argument too.** A rule whose stated reason no
  longer supports it is worse than one with no reason at all.
- **When a rule turns out to have been wrong, say so in the document rather than
  quietly editing it.** The record of a rule that was too broad, and how it was
  narrowed, is what stops the same over-broad rule being written again.
