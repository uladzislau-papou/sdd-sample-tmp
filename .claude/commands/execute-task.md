---
description: Implement one task block from tasks.md, TDD-first, keeping specs in sync
argument-hint: 1.1
---

Implement $ARGUMENTS in file `tasks.md`. Check all implemented steps inside `tasks.md`.

Follow `documentation/execution.playbook.md` § 3 for the increment, and read
`CLAUDE.md` for the authority order first.

Non-negotiable:

- **RED before GREEN.** Write the failing test, run it, and quote the actual
  failure output — command, test name, assertion message — before writing any
  production code (`tdd.definition.md` § 2). No quoted RED, no GREEN.
  A Kotlin compile failure is a valid RED; "it obviously will not compile" is a
  prediction, not evidence.
- **Then GREEN, then REFACTOR**, per `execution.playbook.md` § 3.4.1–3.4.3.
- **Dispatch `ddd-hex-reviewer` and `spec-documenter` in parallel** after REFACTOR
  (§ 3.5). A `DRIFT` verdict blocks the task — fix the finding, do not argue with it.
- **Run the quality gates** in `test.definition.md` § 7:
  `./gradlew clean test` and `./gradlew build`. Single module — no `:app:` prefix.
- **If the task touches persistence, start Postgres first** (`make devup`). The
  `*IT` tests skip themselves when it is unreachable, and a green run in which every
  integration test skipped has not verified the persistence layer. Say which happened
  (`test.definition.md` § 2.3).

Update all specs accordingly when you modify the code. This ensures that the specs
are always up-to-date — `spec-documenter` owns this, but the increment is not
complete until it has run.

Only tick a `tasks.md` box, or a DoD box, when a named passing test or an existing
file proves it. "Implemented" is not evidence (`test.definition.md` § 9).

Do not suppress a compiler warning to get past `allWarningsAsErrors`. Fix it, or
raise it as a finding.

End with the Agent Output Contract in `execution.playbook.md` § 5.
