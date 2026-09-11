---
description: Turn a use case spec into plan.md — find and fill spec gaps, then propose implementation steps
argument-hint: uc05 (or UC05)
---

Based on the requirements defined in use-case $ARGUMENTS, find all necessary
specifications, define missing ones, define missing requirements and architecture
decisions, and create a file `plan.md` in the project root directory.

Resolve the use case by globbing `documentation/use-cases/uc<nn>-*.spec.md` — do
not guess the filename stem.

The `plan.md` file MUST contain the specification, clarification and
implementation steps you want to do. Do not change any files.

Read `CLAUDE.md` for the authority order first, then the always-read documents
listed there.

The plan MUST account for:

- **`## 10. Definition of Done`** — if the spec has none, or has criteria that are
  not objectively checkable, say so. It is the exit condition for `/loop-uc`
  (`loop.playbook.md` § 1), so a vague DoD is a blocking spec defect.
- **`AC-NN` identifiers** in § 7, and which test will cover each
  (`sdd.playbook.md` § 4). A criterion about money or a derived date must state an
  **exact** expected value; if it does not, that is a spec defect to fix before
  planning.
- **TDD ordering** — the plan's steps are RED-first, inside-out per
  `tdd.definition.md` § 3: domain → driver → GraphQL → persistence. Do not plan
  "implement X, then test X".
- **ADR triggers** — check `sdd.playbook.md` § 6, and check § 6.1 before raising
  one. Any genuine trigger means the ADR comes before implementation.
- **Cross-context use cases run the ladder twice.** UC04, UC05, UC08 and UC09 each
  touch two contexts. Plan the domain step in each context separately, before either
  driver exists.
- **The three artefacts a persisted change always needs together**: the Flyway
  migration, the `*Entity` + `*Mapper`, and the round-trip assertion the repository
  port spec demands. A plan that adds a mutable property without all three is
  incomplete.
- **A `SUPERSEDED` spec is not a target.** Point at its successors instead.
- **Which schema file and which `graphql/uc<nn>-*.graphql` request file** the
  increment touches — or state explicitly that the use case has no GraphQL surface
  (UC06, UC08, UC09) and therefore no request file.

Ask questions to clarify if necessary.
