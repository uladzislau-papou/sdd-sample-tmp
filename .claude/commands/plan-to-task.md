---
description: Break plan.md down into a phased, checkbox task list in tasks.md
---

Based on the file `plan.md` in the project root, create a detailed plan in
`tasks.md`.

Break down the tasks incrementally and small.
Make a checkmark before each task `- [ ]` so that you can check it off in the future.
Create only the file `tasks.md`. Don't modify any files yet or start implementing.

Number the task blocks like this:

```markdown
## Phase 1: Domain Foundation

### 1.1 - TourBooking completion transition
- [ ] **Task 1.1**: RED — add `TourBookingTest.markCompleted_happyPath_transitionsToCompleted`, run it, quote the failure.
- [ ] **Task 1.2**: GREEN — add `TourBooking.markCompleted(Instant)`, minimal implementation.
- [ ] **Task 1.3**: REFACTOR — extract the state guard if it duplicates `markActive`.
- [ ] **Task 1.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [ ] **Task 1.5**: Run `./gradlew clean test` and `./gradlew build`.
```

Phase 1 and task block 1.1 together are one piece of implementation you can do in
one iteration, so that a single block can be referenced by number.

Rules:

- Each task block follows **RED → GREEN → REFACTOR** order
  (`tdd.definition.md`). Never a task that implements before its test exists.
- Each block ends with the review/document dispatch and the quality gates
  (`execution.playbook.md` § 3.5–3.6).
- Mirror the use case's `## 10. Definition of Done` into a
  `## DoD Scoreboard – UC<nn>` section at the top of `tasks.md`. The spec is
  authoritative; this is a scoreboard (`loop.playbook.md` § 1).
- Verification tasks use the real commands: `./gradlew clean test`,
  `./gradlew build`. This is a Kotlin/Gradle project.

Wait until I explicitly ask you to do something.
