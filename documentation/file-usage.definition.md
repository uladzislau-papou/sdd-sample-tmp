# File Usage Definition

## Purpose

Defines the authoritative role of each file inside `/documentation`.

It prevents rule duplication, circular governance, and specification drift.

---

# 1. Authority Order (Source of Truth)

**The ranked authority order lives in [`../CLAUDE.md`](../CLAUDE.md) and nowhere else.**

`CLAUDE.md` is loaded into every agent's context automatically, so it must be
self-contained — which makes it the only sensible home for the order.

This document defines what each file is **responsible for**. `CLAUDE.md` defines which
file **wins** when two disagree.

Higher documents override lower ones. Templates do not override definitions.

---

# 2. File Responsibilities

## project.definition.md
Defines: vision · strategic intent · non-goals · success criteria.
No technical detail belongs here.

## architecture.definition.md
Defines: layering · package ontology · dependency rules · framework boundaries ·
**the registry** (§ 11: modules, cross-module edges, warts) · time/identity/audit
placement · the relationship to the README's hexagonal aim (§ 2.2).
Does NOT define modelling semantics.

## modelling.definition.md
Defines: entities-as-domain-model doctrine · mutability · where validation lives ·
service/mapper/repository/state-machine/outbox roles · error taxonomy · event kinds.
Does NOT define package placement.

## coding-style.definition.md
Defines: Kotlin style · naming and class-role suffixes · KDoc standard · null policy ·
logging · error-message style · what the tooling enforces.
Does NOT introduce architectural decisions.

## technical.spec.md
Defines: tech stack and versions · API surface and endpoints · persistence and migration
strategy · configuration · test tooling · gate **commands** · observability.
Does NOT define governance or workflow.

## test.definition.md
Defines: test taxonomy · assertion rules · fixtures · naming · coverage themes ·
**quality gates (merge blockers) — canonical location** · traceability.
Does NOT define *when* a test is written — that is `tdd.definition.md`.

## tdd.definition.md
Defines: the RED → GREEN → REFACTOR cycle · the RED evidence rule · the
behaviour-preserving exception · the brownfield characterization rule · layer ordering ·
TDD anti-patterns.
Refines `test.definition.md`; MUST NOT restate its taxonomy or assertion rules.

## sdd.playbook.md
Defines: spec hierarchy · mandatory spec types · traceability model · AC and DoD rules ·
**ADR triggers — canonical location** (§ 6) · ADR format · scope control · domain
governance.
Does NOT describe step-by-step execution.

## execution.playbook.md
Defines: the **inner** loop — one increment, Read → Spec → Plan → Implement → Review →
Verify → Closeout · the agent output contract.
MUST NOT redefine rules owned by `technical.spec.md`, `modelling.definition.md`,
`test.definition.md`, `tdd.definition.md` or `sdd.playbook.md`. It may reference them.

## loop.playbook.md
Defines: the **outer** loop — repeat the inner loop until a use case's DoD is met · exit
conditions · hard stops · model routing.
MUST NOT redefine the inner-loop phases; it composes them.

## layer-responsibilities.definition.md
Quick-reference clarification of the controller / service / entity / mapper / advice
boundary. Introduces no new rules.

## file-naming.definition.md
Defines filename conventions for this tree and for the artefacts agents create in the
service repository.

## /adr/*.adr.md
Records architectural decisions. **Immutable once accepted.** Retroactive records are
marked as such.

## /domain/entity-*.spec.md
Concrete entity specifications. Must follow `domain/domain.spec.template.md`.

## /integrations/*.{inbound,outbound}.spec.md
Concrete inbound-surface and outbound-integration specifications. Must follow
`integrations/integration.spec.template.md`.

## /use-cases/uc*.spec.md
Concrete use case specifications. Must follow `use-cases/use-case.spec.template.md`.

Each spec owns its `## 10. Definition of Done` — the **authoritative** exit condition for
the outer loop. `tasks.md` mirrors it; the spec always wins.

## notes.md
Scratchpad. Non-authoritative. May not contradict formal definitions.

---

# 3. Template Governance

Templates:

- `/domain/domain.spec.template.md`
- `/integrations/integration.spec.template.md`
- `/use-cases/use-case.spec.template.md`

Templates define structure, not rules. Changing a template does not change governance;
changing governance requires updating the relevant definition file.

---

# 4. No Duplication Rule

Rules MUST appear in exactly one authoritative file.

Three lists are single-sourced and must not be copied:

| List | Canonical location |
|------|--------------------|
| ADR triggers | `sdd.playbook.md` § 6 |
| Quality gates | `test.definition.md` § 7 |
| Registered modules / cross-module edges / warts | `architecture.definition.md` § 11 |

Copy-paste duplication is forbidden. Reference instead.

---

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
for *rules*. A doctrine change and the code it sanctions arriving in one commit are
indistinguishable from the code arriving first and the rule being written afterwards to
authorise it. Separating the commits makes the ordering a fact in the history rather than
an assertion in a report.

Rules:

- Doctrine change → its own commit, whose message states which rule changed and why.
- Anything relying on it → a **later** commit. "Anything" includes **build configuration**:
  applying a plugin, adding a dependency, wiring a task. A gate added to
  `test.definition.md` § 7 and the tooling that enforces it are two commits, not one.
- Never amend a doctrine commit to accommodate code written after it. If the rule turns out
  to be wrong, change it in a new commit and say so.
- A doctrine commit that *loosens* a rule deserves particular scrutiny: tightening a rule
  cannot retroactively legalise existing code, but loosening one can.
- `rms-architecture-reviewer` verifies ordering with
  `git log --diff-filter=M -- documentation/`.

### Recorded exception: the RMS adaptation

This documentation set was adapted wholesale from a prior project's SDD kit and rewritten
against `risk-management-service` as the source of truth. That adaptation is one commit —
or one series — and precedes all RMS code it governs, because none of the existing service
code was written against it.

**§ 5.1 applies from the first increment after the adaptation lands.** Retroactively
slicing the adaptation into doctrine-then-code would fabricate an ordering that did not
happen, and the existing service code was not authored under these rules in the first
place. That is stated here rather than left implicit, so the first reviewer to run
`git log --diff-filter=M -- documentation/` does not report it as a breach.

A rule that lists the times it was not followed is more credible than one that reads as
though it never has been. Add an entry here when it happens again.

---

# 6. Anti-Patterns

- Duplicating governance rules across files
- Letting `execution.playbook.md` redefine architecture
- Letting a template introduce a constraint
- Treating `notes.md` as specification
- Encoding a rule in an agent prompt instead of in the definition file it belongs to
- Adding a row to the § 11.3 wart registry to legalise new code
- Documenting an aspiration as though it were an enforced rule
  (`architecture.definition.md` § 2.2 is the precedent for how to handle one)

Clarity over convenience.

---

# 7. Executable Layer (`.claude/`)

`/documentation` states the rules. `.claude/` executes them. The executable layer is
**subordinate**: it may enforce a documented rule, never invent one.

| Path | Role |
|------|------|
| `.claude/agents/*.md` | Subagent definitions. Each checklist item MUST cite the definition file and section it enforces. |
| `.claude/skills/*/SKILL.md` | Multi-step workflows. Compose playbook phases; do not redefine them. |
| `.claude/commands/*.md` | Single-shot slash commands. |
| `.claude/settings.json` | Checked-in harness config: tool permissions. |
| `.claude/settings.local.json` | Personal overrides. Never authoritative. |

Rules:

- If an agent needs a rule that is not written down, the rule goes into the appropriate
  `.definition.md` **first**, and the agent references it. A checklist item with no
  upstream citation is a governance bug.
- Agents that review MUST NOT edit. Agents that document MUST NOT touch `src/**`. Enforced
  by their `tools:` frontmatter, not by good intentions.
- A hardcoded list inside an agent prompt is duplication under § 4. Machine-checkable facts
  belong in the documents — the registered modules live in
  `architecture.definition.md` § 11.1, and the agent reads them from there.
