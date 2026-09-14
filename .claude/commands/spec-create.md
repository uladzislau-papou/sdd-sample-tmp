---
description: Turn a Jira issue (and optional Confluence page) into one or more Use Case specs — fetch, decompose if needed, write documentation/use-cases/uc<nn>-*.spec.md, and self-review against sdd.playbook.md
argument-hint: <JIRA-KEY-or-link> [confluence-link]
---

Turn the Jira issue (and optional Confluence page) given in $ARGUMENTS into one or
more Use Case Specs under `documentation/use-cases/`, following
`use-case.spec.template.md` exactly.

Read `CLAUDE.md` for the authority order first, then `sdd.playbook.md`,
`file-naming.definition.md`, and `architecture.definition.md` § 11 (registered
bounded contexts) — this command writes files, so get the rules right before
writing anything.

**Do not guess.** If the ticket is silent on something the template requires —
an exact money or date value, a failure classification, which bounded context
owns the flow — that is a gap in the source material, not something to infer
plausibly. List it as an open question instead of inventing an answer
(`sdd.playbook.md` § 4.1: an uncommitted number is a defect, not a placeholder
worth guessing at).

## 1. Fetch

Resolve `$ARGUMENTS` into a Jira issue key/link and an optional Confluence
link. Use the Atlassian MCP tools (`getJiraIssue`, `getConfluencePage`, and
`search`/`searchJiraIssuesUsingJql`/`searchConfluenceUsingCql` if you only have
a partial reference) to pull the issue and any linked/referenced Confluence
pages. If a reference can't be resolved, stop and ask rather than guessing
which issue was meant.

## 2. Analyze

For each candidate use case implied by the fetched content, work out:

- **Is this a new use case, or a change to an existing one?** Grep
  `documentation/use-cases/*.spec.md` for the same aggregate/trigger/intent
  first. A ticket that amends behaviour already specified updates that file's
  relevant sections — it does not get a new `uc<nn>`.
- **Bounded context.** Must be one of the rows in `architecture.definition.md`
  § 11. If the ticket implies a context that doesn't exist yet, that is ADR
  trigger 9 (`sdd.playbook.md` § 6) — halt and report it; do not write a spec
  for an unregistered context.
- **Any other ADR trigger** (`sdd.playbook.md` § 6, exceptions in § 6.1). If one
  fires, halt and report it before writing anything else the ticket asked for.

## 3. Decompose, if needed

If the fetched content genuinely covers more than one use case (distinct
triggers, aggregates, or flows), do not create files yet. Propose the
breakdown — one line per candidate `uc<nn>` with its intent and bounded
context — and ask the user to confirm the split before proceeding. Proceed
straight to step 4 if the ticket is already a single use case.

## 4. Write

For each confirmed new use case:

1. Assign the number: glob `documentation/use-cases/uc<nn>-*.spec.md`, take the
   highest `<nn>`, use `max + 1`, zero-padded (`file-naming.definition.md`).
   Numbers are stable once assigned — never reuse or renumber an existing one.
2. Fill every section of `use-case.spec.template.md` in order — Status,
   Bounded Context, Purpose, §§ 1–10. Do not reorder, renumber, or omit a
   section; a section that doesn't apply says so explicitly
   (`use-case.spec.template.md` header comment).
3. Note the source in the Purpose section prose: the Jira key/title and, if
   used, the Confluence page title/link. Not a new heading — just a sentence.
4. § 7 Acceptance Criteria: stable `AC-NN` ids, each describing domain
   behaviour with a committed value where money or a date is involved
   (`sdd.playbook.md` § 4.1).
5. § 10 Definition of Done: every `AC-NN` cited by at least one item, every item
   objectively checkable (`sdd.playbook.md` § 4.2), mirroring the template's
   Behaviour / Contracts / Governance grouping.
6. Status is `SPECIFIED` — this command produces specs, not implementations.
7. Write to `documentation/use-cases/uc<nn>-<kebab-name>.spec.md`.

## 5. Self-review

Before reporting done, check the file(s) just written against this checklist
and fix anything that fails it:

- [ ] Filename matches `uc<nn>-<kebab-name>.spec.md`, `<nn>` zero-padded and
      sequential (`file-naming.definition.md`)
- [ ] All template sections present in order, none renumbered; inapplicable
      sections explicitly say why
- [ ] Bounded Context is a row in `architecture.definition.md` § 11
- [ ] Every `AC-NN` in § 7 is cited by at least one § 10 item
      (`sdd.playbook.md` § 4.2)
- [ ] Every § 10 item is objectively checkable — a named test, an existing
      file, a `PASS` verdict, a green gate — not "code is clean"
      (`sdd.playbook.md` § 4.2)
- [ ] Any AC about money or a derived date states the exact expected value
      (`sdd.playbook.md` § 4.1)
- [ ] § 8 Failure Scenarios and § 9's classification mapping agree with each
      other
- [ ] § 9 GraphQL Contract is either filled in or explicitly marked
      "Not applicable — event-driven | inport-triggered", and every
      classification it lists has a home in `graphql/uc<nn>-*.graphql` (to be
      added at implementation time, not by this command)
- [ ] Nothing in the spec silently absorbs an ADR trigger from
      `sdd.playbook.md` § 6

## 6. Report

Summarize: what was fetched (Jira key + title, Confluence page if any),
whether a decomposition happened and what was confirmed, which file(s) were
written, the self-review outcome, and any open questions or ADR flags raised
along the way. Next step for the caller is normally `/uc-to-plan uc<nn>`.

Ask questions to clarify if necessary — before starting, and at any point the
source material doesn't give you what the template requires.
