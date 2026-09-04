---
name: spec-create
description: Fetch a Jira/Confluence ticket (by key or link), analyze it, and turn it into one or more use-case specs following use-case.spec.template.md. Decomposes Epics/Features into clean per-outcome specs, follows the ticket's own child-issue/child-page breakdown as a starting split, halts on ADR triggers per unit, and asks clarifying questions instead of guessing. Use when asked to turn a ticket, epic, or requirements doc into a use-case spec.
argument-hint: <ticket-key-or-URL>
---

# Create Use-Case Specs From a Ticket

Doctrine: `documentation/sdd.playbook.md`, `documentation/file-naming.definition.md`,
`documentation/use-cases/use-case.spec.template.md`. Read all three before
starting — this file is the procedure, those are the reasoning and the contract.

You are the **decomposition driver**. You own the split proposal, the per-unit
gate checks, and the report. You never silently guess a gap, and you never draft
around a halted unit — you halt that unit and move to the next.

## Argument

`<ticket-key-or-URL>` — a bare ticket key (e.g. `PROJ-123`), a full Jira/Confluence
URL, or absent (then ask for pasted content directly — see Step 0).

---

## Step 0 — Acquire source content

1. Discover Jira/Confluence MCP tools available at runtime (search connected MCP
   tools by name/keyword; do not hardcode a specific server). Do not assume one is
   configured — none is configured in this repo as of today.
2. **If found:** resolve the argument (ticket key or URL) and fetch:
   - the ticket/page itself,
   - if it is an Epic/Feature: its linked child issues, each fetched in full,
   - if it is a Confluence page with child pages: those child pages, fetched in full,
   - any Confluence page linked from within the ticket's description or comments,
     fetched in full.
3. **If no MCP tools are found:** do not fail silently and do not fail hard either.
   Tell the user no Jira/Confluence MCP tool is available, and offer the fallback:
   ask them to paste the ticket/epic content (and any child-issue or linked-page
   content) directly. Proceed with the pasted text exactly as if it had been
   fetched — the analysis pipeline below does not care about the source.
4. If nothing usable was obtained (no MCP, and nothing pasted), stop here and say so.

---

## Step 1 — Propose the split

This is the only step that looks at the *whole* input at once — everything after
this is per-unit.

1. **Starting split.** If the input has an existing human-authored breakdown
   (Jira child issues under an Epic, or Confluence child pages), that breakdown
   *is* the starting set of candidate units. If the input is a single, childless
   ticket/page, the starting set is that one unit.
2. **Refine with four signals.** For each candidate unit, and across pairs of
   candidate units, check:
   - spans more than one module (registered modules are in
     `architecture.definition.md` § 11.1)
   - spans more than one consistency-anchor entity
     (`modelling.definition.md` § 2.4)
   - spans more than one distinct trigger (e.g. one GraphQL mutation + one
     provider webhook + one scheduler)
   - would force § 7 Acceptance Criteria to cover unrelated business outcomes
     that don't share one § 1 Intent
   Any signal justifies **splitting** a unit further. The *absence* of all four
   signals between two sibling units justifies **merging** them — two units that
   differ on none of these describe one business outcome, not two.
3. **Present the proposed split** to the user: one line per resulting unit, with
   a working title and a one-sentence justification citing which signal(s) drove
   any split or merge relative to the starting breakdown. State it plainly if the
   proposed split is identical to the starting breakdown (no signal fired).
4. **Wait for confirmation.** Do not proceed to Step 2 for any unit until the
   user confirms the split. If they adjust it, use their adjusted split.

---

## Step 2 — Per-unit pipeline

Run this once per confirmed unit, in any order. A halt on one unit never blocks
the others — report and continue.

### 2.1 — ADR-trigger scan

Check the unit's requirements against the full canonical list in
`sdd.playbook.md` § 6 — **all seventeen triggers**, not only the module case.

The RMS-specific ones fire more often than people expect and are the ones most
often missed at spec time:

- **14 — a new provider**, or a change to which provider is the default for an
  operation. A provider is a contract, an SLA and a failure mode, not a config value.
- **15 — anything touching authentication or authorization**: a new scope, a
  change to how operator identity is established, a surface with a different
  auth mechanism.
- **16 — a change to the audit contract** (envelope, transport, guarantee).
  Note: *adding* an event to the existing contract is a catalogue update, not an ADR.
- **17 — a change to how personal data is stored, hashed, retained or deleted.**

Also watch for: a new cross-module edge or an inverted one (10), a new outbound
delivery needing infrastructure (3), a widened transaction (5).

**Any trigger fires → halt this unit.** Report which trigger, why, and what the
user needs to do (write the ADR, and — for a new module specifically — register it
in `architecture.definition.md` § 11.1) before this unit can be specced. Do not
draft the spec anyway "for now." Move to the next unit.

### 2.2 — Gap check

Compare the unit's available content against every section the template
requires: § 1 Intent, § 2 Input Contract, § 3 Output Contract (including the
error-type table), § 4 Preconditions, § 5 Flow, § 6 Side Effects, § 7 Acceptance
Criteria (each needs a stable `AC-NN`, Given/When/Then, and must describe
business behaviour — not implementation), § 8 Failure Scenarios, § 9 API
Contract (or the explicit "Not applicable — scheduler/internal").

RMS-specific gaps that a ticket almost never supplies and you must ask about
rather than infer:

- **Which lifecycle states is this legal from**, and what happens from each
  illegal one — a message, a classification, a status
- **Which scope** the caller must hold, and whether an operator must be present
  or a service actor is acceptable
- **Which audit event** fires, and its actor
- **Idempotency**: is a repeat safe, and what makes it safe
- **For a provider-facing unit**: the complete external vocabulary, so the
  integration spec's § 3 translation table can be exhaustive. A partial list of
  provider statuses is the single most expensive gap to discover later

For anything missing or ambiguous, **ask the user** — do not draft best-effort
content or leave a TODO. A spec with an unresolved gap in § 7 or § 10 is not
DoD-checkable, which is the one property the rest of the pipeline
(`/uc-to-plan` → `/plan-to-task` → `/loop-uc`) depends on.

### 2.3 — Draft § 10 Definition of Done

Following the template's rules exactly:
- every `AC-NN` from § 7 is cited by at least one item,
- every item is objectively checkable (named test, existing file, `PASS`
  verdict, green gate) — never a vague statement,
- test-backed items name the test class/method even though the test doesn't
  exist yet (it is the target the future TDD cycle writes to).

Use the three subsections from the template: `### Behaviour`, `### Contracts`,
`### Governance`.

### 2.4 — Assign the number and write the file

1. Assign `uc<nn>` **now, lazily** — scan `documentation/use-cases/uc<nn>-*.spec.md`
   for the current highest number and take the next one. Do this immediately
   before writing, not at Step 1, so a halted unit never reserves a number it
   doesn't use and the sequence stays gap-free.
2. Check for an existing file at that path or any file already describing this
   same use case. **If a spec for this use case already exists, stop and ask**
   whether to target a different use case or handle it another way — never
   overwrite silently.
3. Write `documentation/use-cases/uc<nn>-<kebab-name>.spec.md`, filled in
   against `use-case.spec.template.md` exactly — same section order, same
   headings, no renumbering. Sections that don't apply say so explicitly
   ("Not applicable — ...").
4. Do not write `rest/*.http` files here — that happens at implement phase
   (`CLAUDE.md`, API Request Documentation), not spec creation.
5. If the unit needs a **domain spec** (new entity or a changed lifecycle) or an
   **integration spec** (new surface or provider contract), say so in the report
   and offer to write them from
   `documentation/domain/domain.spec.template.md` and
   `documentation/integrations/integration.spec.template.md`. Do not write them
   unasked — the use case spec is this skill's deliverable.

Write directly once 2.1–2.3 are clear for this unit — the split confirmation
(Step 1) and the gap-resolution loop (2.2) already gate the content; there is no
additional draft-approval step.

---

## Step 3 — Report

End with a structured, per-unit summary — this project reports in checklists
(DoD scoreboards, Agent Output Contracts), not prose:

```
Source          RISK-123 (Epic) — 3 child issues, 1 linked Confluence page
Proposed split  3 units (matches Jira breakdown; no merge/further-split signals fired)

Unit 1  uc13-decline-kyc-case-with-reason.spec.md  WRITTEN
        Also needs: entity-kyc-case.spec.md § 4 lifecycle row (offered)
Unit 2  uc14-deliver-decision-to-radar.spec.md     WRITTEN
        Also needs: radar-decisions.outbound.spec.md (offered)
Unit 3  onfido-identity-provider                   HALTED
        Trigger: sdd.playbook.md § 6.14 (onboarding a new provider)
        Action needed: write an ADR for the provider decision, then re-run
        /spec-create against this unit's source content.

Next    Run /uc-to-plan uc13, /uc-to-plan uc14 for the written specs.
```

---

## Anti-patterns

- Drafting a spec for a unit that hit an ADR trigger "to keep moving"
- Filling § 7/§ 10 with best-effort content or TODOs instead of asking
- Reserving a `uc<nn>` for a unit before it's confirmed it will actually write
- Overwriting an existing spec file without asking
- Treating the Jira/Confluence child breakdown as final without checking the
  four refinement signals, or discarding it without a signal-backed reason
- Generating `rest/*.http` files at this phase
- Leaving a provider translation table partial because the ticket only listed the
  statuses the happy path uses
- Reporting the outcome as prose instead of a per-unit checklist
