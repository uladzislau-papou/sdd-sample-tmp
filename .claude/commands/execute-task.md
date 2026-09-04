---
description: Implement one task block from tasks.md, TDD-first, keeping specs in sync
argument-hint: 1.1
---

Implement $ARGUMENTS from `tasks.md`. Tick the boxes you actually complete.

Follow `documentation/execution.playbook.md` § 3 for the increment, and read `CLAUDE.md`
for the authority order first.

Non-negotiable:

- **RED before GREEN.** Write the failing test, run it, and quote the actual failure
  output — command, test name, assertion message — before writing any production code
  (`tdd.definition.md` § 2). No quoted RED, no GREEN.
  - If the test unexpectedly **passes**, that is a finding, not a formality to skip. Apply
    `tdd.definition.md` § 2.2 — common in this brownfield codebase.
  - If you are modifying an untested service method, write a characterization test for its
    current behaviour first and land it as its own step (`tdd.definition.md` § 2.3).
- **Then GREEN, then REFACTOR**, per `execution.playbook.md` § 3.4.1–3.4.3.
- **Dispatch `rms-architecture-reviewer` and `spec-documenter` in parallel** after REFACTOR
  (§ 3.5). A `DRIFT` verdict blocks the task — fix the finding, do not argue with it.
- **Run the quality gates** in `test.definition.md` § 7:
  ```shell
  ./gradlew spotlessApply && ./gradlew spotlessCheck && ./gradlew detekt && ./gradlew test && ./gradlew build
  ```
  Single-module build — no `:app:` prefix. `spotlessApply` always before `detekt`.

Update all specs when you modify the code — use case, domain and integration specs, and
`rest/uc<nn>-*.http`. `spec-documenter` owns this, but the increment is not complete until
it has run.

Before you report done, confirm the RMS-specific things that are easiest to forget:

- new configuration variable → present in `.env.example`
- new or changed audit event → present in the `docs/` catalogue
- new GraphQL operation → present in both the `.graphqls` schema and the controller
- new enum mapping → exhaustive, and exhaustively tested
- new endpoint → declared scope, plus 401 / 403 / pass-through tests
- schema change → a **new** timestamp-named migration; no applied migration edited
- no `@Suppress` added to reach green; no assertion weakened or deleted

Only tick a `tasks.md` box, or a DoD box, when a named passing test or an existing file
proves it. "Implemented" is not evidence (`test.definition.md` § 9).

End with the Agent Output Contract in `execution.playbook.md` § 5.
