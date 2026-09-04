---
description: Break plan.md down into a phased, checkbox task list in tasks.md
---

Based on the file `plan.md` in the project root, create a detailed task list in
`tasks.md`.

Break the work down incrementally and small. Put `- [ ]` before each task so it can be
checked off. Create only the file `tasks.md`. Don't modify any other files and don't start
implementing.

Number the task blocks like this:

```markdown
## Phase 1: Case decision service

### 1.1 - Decline a case from a terminal state is rejected
- [ ] **Task 1.1**: RED — add `KycCaseDecisionServiceTest.declineCase_throwsBadUserInput_whenCaseIsDeclined`, run it, quote the failure.
- [ ] **Task 1.2**: GREEN — reject the illegal transition in `KycCaseDecisionService`, minimal implementation.
- [ ] **Task 1.3**: REFACTOR — extract the guard if it duplicates `acceptCase`.
- [ ] **Task 1.4**: Dispatch `rms-architecture-reviewer` and `spec-documenter`.
- [ ] **Task 1.5**: Run `./gradlew spotlessApply detekt test build`.
```

Phase 1 and task block 1.1 together are one piece of implementation you can do in one
iteration, so that a single block can be referenced by number.

Rules:

- Each task block follows **RED → GREEN → REFACTOR** order (`tdd.definition.md`). Never a
  task that implements before its test exists.
- Order the phases **service → mapper → controller → authorization → integration**
  (`tdd.definition.md` § 3). Deviate only where the plan justifies it.
- Each block ends with the review/document dispatch and the quality gates
  (`execution.playbook.md` § 3.5–3.6).
- Mirror the use case's `## 10. Definition of Done` into a `## DoD Scoreboard – UC<nn>`
  section at the top of `tasks.md`. The spec is authoritative; this is a scoreboard
  (`loop.playbook.md` § 1).
- Verification tasks use the real commands: `./gradlew spotlessApply`, `./gradlew detekt`,
  `./gradlew test`, `./gradlew build`. This is a **single-module** Gradle build — there is
  no `:app:` prefix. `spotlessApply` always runs before `detekt`.

Give these their own tasks rather than folding them into an implementation step, because
they are the ones that get forgotten:

- a characterization test before modifying untested code (`tdd.definition.md` § 2.3)
- the Flyway migration, as a separate task naming the timestamp filename
- the GraphQL schema edit, paired with the controller task
- the exhaustive mapping test when an enum translation changes
- the 401 / 403 / pass-through authorization tests for any new surface
- the `.env.example` entry for any new configuration variable
- the `docs/` catalogue entry for any new or changed audit event
- the `rest/uc<nn>-*.http` requests, one per documented outcome

Wait until I explicitly ask you to do something.
