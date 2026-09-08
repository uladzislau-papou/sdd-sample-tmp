# File Usage Definition

## Purpose

This document defines the authoritative role of each file inside the `/documentation` directory.

It prevents rule duplication, circular governance, and specification drift.


# 1. Authority Order (Source of Truth)

**The ranked authority order lives in [`../CLAUDE.md`](../CLAUDE.md) and nowhere else.**

`CLAUDE.md` is loaded into every agent's context automatically, so it must be
self-contained — which makes it the only sensible home for the order. This file
previously carried a second, divergent ranking; that copy has been removed under
§ 4 (No Duplication Rule).

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
- Success criteria

No technical detail belongs here.


## architecture.definition.md
Defines:
- Layering
- Package ontology
- Ports & Adapters rules
- Framework boundary rules

Does NOT define domain modeling semantics.


## modelling.definition.md
Defines:
- Aggregates
- Entities
- Value Objects
- Domain Events
- Always-Valid doctrine

Does NOT define use case orchestration.


## technical.spec.md
Defines:
- Tech stack decisions
- Tooling constraints
- Database strategy
- Migration strategy
- SQL classification rules

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
Defines how the actual code should look like.
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

## 4.1 Cite the owning document; do not paraphrase it

The rule above bans copy-paste. **A paraphrase is the harder case**, and it is the one that
actually happens: restating another document's claim in your own words produces a copy that no
grep will find and no reader will recognise as a copy — so when the owner changes, the
paraphrase silently becomes false.

Therefore: a statement of fact about code or about another document's decision belongs in the
file that **owns** it (§ 2), and every other file **cites** it. Write "see
`domain-event-publisher.outport.spec.md` § 4", not a sentence that says the same thing
differently.

This applies to statements about **code** as much as to statements about rules. A spec asserting
something false about the implementation is caught by `test.definition.md` § 7 gate 13, but that
gate is about a spec describing the *previous* state; a paraphrase that was never true is a
different defect and this clause is where it is named.

> **Adopted after five instances in one increment.** UC07 corrected
> `DomainEventPublisher`'s KDoc, which had said the implementation "delivers after commit" —
> `adr/0022` superseded that and never propagated. The correction then asserted that
> `LoggingDomainEventPublisher` "logs", which is false: it delegates to Spring's
> `ApplicationEventPublisher` and the *listener* logs. That claim was inferred from the class
> name and never read against the class. It was then repeated twice more in `uc07`'s own spec —
> in the very passage describing the fix — and the port spec's own Purpose paragraph was left
> asserting what its § 3 had been rewritten to withdraw.
>
> Three separate `ddd-hex-reviewer` rounds were needed, each finding what the previous fix had
> introduced. That is not a lapse of care; it is what paraphrase does. `HANDOFF.md` § 5 names
> the class as **a summary is not a source** and predicted a fourth victim — this clause exists
> because it found the fourth, fifth, sixth and seventh.
>
> No test enforces this, and that is a known gap: `ddd-hex-reviewer` observed that no rule
> prohibited a spec from asserting something false about the code, which is why it had to cite
> § 4 and § 5 step 3 for a finding that fitted neither exactly. ADR-0014 says a rotted rule
> should get an executable owner rather than a firmer restatement — a grep for paraphrase is not
> possible, but a rule stated plainly is at least a rule a reviewer can cite.


# 5. Change Protocol

When modifying rules:

1. Identify highest authoritative file.
2. Update that file.
3. Remove duplicated rules from lower documents.
4. Document change in ADR if architectural.
5. **Commit the doctrine change on its own, before any code that relies on it** (§ 5.1).

## 5.1 Doctrine Lands First

**A change to a `*.definition.md` or `*.playbook.md` lands in its own commit, before any
code that relies on it.**

Why this is a rule and not a preference: `tdd.definition.md` § 2 makes TDD auditable for
code — the quoted RED failure is the evidence. Nothing made spec-before-code auditable
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
  `git log --diff-filter=M -- documentation/`, replacing its previous
  "ordering unverifiable from a single snapshot" finding.
- **Exempt: a row in `architecture.definition.md` § 11.** The registry is parsed
  **bidirectionally** by `ContextRegistryTest` — a package with no row fails, and a row with
  no package fails — so a doctrine-only commit adding the row breaks the build, and so does
  the reverse. The row and the package MUST land in the same commit, and the commit message
  states which context and cites its ADR.

  This is a structural conflict between this rule and § 11's own gate, not a licence. It is
  written as an exemption rather than added to the violations list below because it will
  recur for **every** future bounded context: a rule that is violated by design every time
  is a rule with a missing clause, not a repeated lapse. Surfaced by `ddd-hex-reviewer` while
  reviewing the `mlc` increment, which could not satisfy both documents at once.

  Note what stays governed. Two doctrine changes UC07 also made carry **no** bidirectional gate,
  so both are ordinary § 5.1 cases and must land in an earlier commit than the code relying on
  them:

  - `architecture.definition.md` § 8.1's `inbound.graphql` row, before `TimestampRulesTest`;
  - `modelling.definition.md` § Identity's third identity placement, before the `mlc` value
    objects that rely on it.

  The second was omitted from this note on its first draft and added after
  `ddd-hex-reviewer` pointed out it sits in the identical position — an exemption list that
  names one of two identical cases is worse than none, because it reads as though the other was
  considered.

Adopted after `ddd-hex-reviewer` observed that a `test.definition.md` § 1.3 rule naming
`WebTestApplication`/`GuideWebTestApplication` had arrived in the same uncommitted tree
as those classes. It declined to call it drift — the rule tightened rather than
legalised — but correctly reported that the ordering could not be verified.

### Recorded violations

Kept deliberately. A rule that lists the times it was broken is more credible than one
that reads as though it never has been, and the two entries here are exactly the cases a
carve-out would have been written to excuse.

- **`a87d98c`** — the commit that adopted this rule. Doctrine and code co-evolved across
  one exploratory session; slicing it retroactively into doctrine-then-code would have
  fabricated an ordering that did not happen. The rule applies from `9f14103` onward.
- **`d7c494e`** — added `spotlessCheck` to the § 7 gate list *and* applied the Spotless
  plugin that satisfies it, in one commit. Found by self-audit
  (`git log --diff-filter=M -- documentation/`). This is what prompted the build-config
  clarification above: the ambiguity was real, and the honest resolution is that build
  config counts, not that this commit was fine.

- **The § 1.4 nullable-record-component commit** (UC08 increment) — bundled the § 1.4
  doctrine change with a production deletion (`TourBookingCancelled.java`) and two Javadoc
  edits. The deletion was UC08 work with no connection to § 1.4; it arrived because an
  earlier `git rm` had staged it and committing with explicit paths still commits the whole
  index. The result was worse than a § 5.1 breach: `TourBooking` at that commit still
  imported and instantiated the deleted class, so **the commit did not compile** and gates
  1–2 failed at it. Found by `ddd-hex-reviewer`. Nothing had been pushed, so the commit was
  rebuilt as doctrine-only and the deletion moved to the UC08 commit that follows.

The first two are not corrected by rewriting history: both were settled, and slicing them
retroactively would have fabricated an ordering that did not happen. The third is
different, and the distinction is worth stating so it is not read as a licence. What was
rewritten there was not the *ordering* — the doctrine genuinely preceded the code either
way — but an unpublished commit that failed to build. Leaving a non-compiling commit in the
history of a reference project is a defect in its own right, separate from § 5.1.

So the rule is: **never rewrite to improve how the ordering looks; you may rewrite to
repair a commit that does not build, and when you do, the breach still gets an entry here.**
A doctrine commit is not amended to accommodate later code, and that applies to the record
of its own breaches too — which is why this entry exists even though the commit it
describes no longer does.


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