---
name: spec-create
description: Turns a Jira ticket or Confluence page into one or more use-case specifications. Reads the ticket and one hop of linked context, proposes a decomposition and waits for confirmation, writes the specs against the template, then dispatches spec-reviewer. Read-only against Jira and Confluence. Use when starting work from a ticket.
argument-hint: WEW-212 | https://site.atlassian.net/browse/WEW-212 | <confluence page url>
---

# Ticket → Specification

You produce the **input** to `/uc-to-plan`. The pipeline is
`/spec-create` → `/uc-to-plan` → `/plan-to-task` → `/execute-task` → `/loop-uc`, and
everything downstream inherits whatever you get wrong here — silently, because a plan built
on an invented acceptance criterion looks exactly like one built on a real requirement.

Doctrine: `documentation/sdd.playbook.md` for governance,
`documentation/use-cases/use-case.spec.template.md` for the structure, which is contractual.

## Where you stop

You write **use-case specifications and nothing else.**

You do **not** write domain specs, port specs or ADRs. A ticket knows the requirement. It
does not know your aggregates, your ports or your invariants — those are derived from an
architectural reading of the code, which happens in the Spec Phase of the inner loop
(`execution.playbook.md`). Generating them here means inventing aggregate names that later
have to be renamed, after `plan.md` and the specs already cite them.

You **do** report which ADR triggers fired. That is different: the ticket is the cheapest
moment to notice that a task needs an architectural decision, and a warning is not a
document.

## Step 1 — Read, one hop out

Use the Atlassian MCP tools. **Read only.** Never comment, never edit, never transition an
issue, never create a subtask — see "Nothing is written back" below.

Read the named artifact, plus exactly one hop:

- the issue's **comments** — where the requirement usually actually lives
- linked Confluence pages and remote links
- linked issues
- for an epic: its child issues

Do not follow a second hop. Two hops reaches somebody else's epic and drowns the context.

### Rule 1 — conflicts are reported, not resolved

A later comment contradicting the description is the normal state of a ticket, not an
anomaly.

**A later comment outranks the description. But the conflict is never resolved silently.**
It becomes an `OPEN QUESTION` in the spec carrying both versions and their dates.

You are not the arbiter of requirements. Picking the newer text and moving on produces a
spec that is confidently wrong in a way nobody can see.

### Rule 2 — unread content is declared

Requirements arrive in screenshots, PDFs and spreadsheets. You cannot read those. **List
every attachment you could not read as an `OPEN QUESTION`.**

Known limitation, so state it rather than rediscovering it: the Confluence scopes granted
cover pages and comments but **not attachments**. So even a page you read fully may have
carried its real content in an attached file.

Silently skipping an attachment is the quietest way to lose half a requirement, because the
resulting spec looks complete.

## Step 2 — Propose a decomposition, then stop

Work out how many use cases the ticket contains. Present them as a list — one line of
intent each — and **wait for confirmation before writing any file.**

The criterion:

> One use case = one command on one inbound port = one aggregate = one transaction boundary
> = one state transition.
>
> A requirement touching two aggregates is two use cases plus the link between them: a
> domain event, or a synchronous call to the other context's published inport
> (`architecture.definition.md` § 11 rule 3).

Why you stop here rather than deciding. Decomposition is the most expensive decision in the
pipeline: it propagates into `plan.md`, `tasks.md`, package names, the files in `api/` and
the DoD scoreboard, and it unwinds only through the `SUPERSEDED` procedure. Confirmation
costs one exchange.

There is precedent in this repository. `uc10-guide-actions.spec.md` once stacked three use
cases in one document under the heading `NOT SURE IF NECESSARY!` and had to be split into
three. Getting that split right needed a fact no ticket contained — that one of the three
was the only publisher of an event another use case depended on, so without it that use case
had no trigger at all. You may spot such a thing. You may equally miss it, and a human
reading your proposal is cheaper than discovering it at the third increment.

## Step 3 — Write the specifications

One file per use case: `documentation/use-cases/uc<nn>-<kebab-name>.spec.md`.

**Numbering.** Highest existing `uc<nn>` plus one. **Re-check immediately before writing** —
between reading and writing, somebody else may have taken the number. If you collide anyway,
rename the file and its one reference now, while the spec is still fresh and nothing points
at it (`file-naming.definition.md`).

**Fill § 1 `Source`** with the ticket key. This is not bookkeeping: `conformance-reviewer`
follows that field back to the original request to answer "did we build what was asked". A
spec with no source can only be checked against itself.

Fill every section of the template. Where a section does not apply, say so explicitly —
`Not applicable — <reason>` — and never leave it blank. § 9 covers both transports; a use
case with no external API states that, and gets no file in `api/`.

### Every statement is sourced or marked

Each claim in §§ 1–8 either traces to a source you name, or to a document higher in
`CLAUDE.md`'s authority order that you cite, or it is an `OPEN QUESTION`. **There is no
fourth option.**

`OPEN QUESTION` is a permitted end state and is not failure. A spec with three open
questions is more useful than one where three gaps were closed by guessing, because the
guesses are indistinguishable from the sourced parts.

It is not free, though: `/loop-uc` refuses a spec whose open questions touch §§ 2, 3, 7 or 9.
Say which category each falls in.

## Step 4 — Report the ADR triggers

Check the ticket against the canonical list in `sdd.playbook.md` § 6 — the canonical list,
read from that file, never copied into this one.

Report which fired and why. **Write no ADR.** You cannot record the rationale for a decision
nobody has made yet.

## Step 5 — Dispatch `spec-reviewer`

Hand it the specs you wrote and the sources you read.

It reviews for **invention** — claims with no source — which is the failure mode you cannot
catch in yourself, because the part of you that filled a gap does not remember it as filling.
That asymmetry is the whole reason it is a separate agent.

Report its verdict. On `GAPS`, fix and re-dispatch. Do **not** resolve a finding by deleting
the claim it flagged unless the claim was genuinely unfounded — deleting a real requirement
to reach `PASS` is worse than the finding.

## Nothing is written back

Jira and Confluence are **sources**. Everything you produce lands in the repository.

The write scopes exist on the connection, so this is a decision rather than a limitation:

- **No comments.** A comment is visible to the whole team and to the customer. A skill that
  comments on every run comments during debugging and on every re-run after a fix, and the
  ticket is unusable within a week.
- **No page publishing.** A spec in two places is two specs. `CLAUDE.md`'s authority order
  is built on the files in `documentation/`; a Confluence copy either drifts or needs
  synchronising forever, and nobody would see an `OPEN QUESTION` or a `SUPERSEDED` status
  there in time.
- **No subtasks.** Creating them is an irreversible act in a shared tracker, based on a
  decomposition that was confirmed in a chat rather than by the team. A wrong decomposition
  in files is fixed by editing files.

## Output

```
Sources read       – ticket, pages, comments, linked issues; each named
Unread             – attachments and content the tooling could not read (or "none")
Decomposition      – the use cases, one line of intent each   [awaits confirmation]
Written            – the spec files created
Open questions     – each one, and whether it blocks /loop-uc (§§ 2, 3, 7, 9)
Source conflicts   – both versions and their dates (or "none")
ADR triggers       – which of sdd.playbook.md § 6 fired (or "none")
spec-reviewer      – verdict and unresolved findings
```

## Anti-patterns

- **Writing the spec before the decomposition is confirmed.** The point of stopping is that
  the answer changes what you write.
- **Resolving a source conflict silently.** Both versions, with dates, as an open question.
- **Filling § 7 with restated intent.** A criterion nobody could disagree about the passing
  of. "Behaves correctly" is not one.
- **Inventing an aggregate name.** Not yours to name here.
- **Closing an open question to look finished.** The gap does not go away; only its
  visibility does.
