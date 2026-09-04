---
description: Turn a use case spec into plan.md — find and fill spec gaps, then propose implementation steps
argument-hint: uc05 (or UC05)
---

Based on the requirements defined in use-case $ARGUMENTS, find all necessary
specifications, define missing ones, define missing requirements and architecture
decisions, and create a file `plan.md` in the project root directory.

Resolve the use case by globbing `documentation/use-cases/uc<nn>-*.spec.md` — do not guess
the filename stem.

The `plan.md` file MUST contain the specification, clarification and implementation steps
you want to do. Do not change any other files.

Read `CLAUDE.md` for the authority order first, then the always-read documents listed
there.

The plan MUST account for:

- **`## 10. Definition of Done`** — if the spec has none, or has criteria that are not
  objectively checkable, say so. It is the exit condition for `/loop-uc`
  (`loop.playbook.md` § 1), so a vague DoD is a blocking spec defect.
- **`AC-NN` identifiers** in § 7, and which test will cover each (`sdd.playbook.md` § 4).
- **TDD ordering** — steps are RED-first, service-first per `tdd.definition.md` § 3
  (service → mapper → controller → authorization → integration). Do not plan
  "implement X, then test X".
- **ADR triggers** — check all seventeen in `sdd.playbook.md` § 6. Any trigger means the
  ADR comes before implementation. Pay particular attention to the RMS-specific ones:
  a new provider (14), an auth or scope change (15), an audit contract change (16), a
  personal-data handling change (17).
- **A `SUPERSEDED` spec is not a target.** Point at its successors instead.

RMS-specific things the plan MUST call out explicitly where they apply:

- **Module and cross-module edges.** Which module owns this, and does the work add an edge
  not registered in `architecture.definition.md` § 11.2?
- **Schema change** → a new timestamp-named Flyway migration, never an edit to an applied
  one. If a NOT NULL column lands on a populated table, name the default or backfill.
- **GraphQL schema change** → the `.graphqls` file and the controller method, planned
  together.
- **Provider contract** → which translation table changes, and whether the mapping stays
  exhaustive.
- **Authorization** → the scope required, and the 401/403/pass-through tests.
- **Audit** → which events fire, and the `docs/` catalogue update.
- **Configuration** → new variables and their `.env.example` entries.
- **Concurrency** → whether the entity needs `@Version`, and what a lock conflict looks
  like to the caller.
- **Existing untested code being modified** → a characterization test first
  (`tdd.definition.md` § 2.3), planned as its own step.

Name the risks in the plan's risk section using the categories in
`execution.playbook.md` § 3.3: provider-contract, migration-on-populated-table,
concurrency, audit-contract, personal-data exposure.

Ask questions to clarify if necessary.
