---
name: loop-uc
description: Run the outer loop-driven-development cycle on one use case until its Definition of Done is met. Iterates RED → GREEN → REFACTOR → drift review → document, re-reading the spec's DoD each pass, and stops on DoD-complete, BLOCKED (no progress), max-iterations, or an ADR trigger. Use when asked to implement or finish a use case end-to-end.
argument-hint: UC06 [--max-iterations=10] [--dry-run]
---

# Loop-Driven Development on a Use Case

Doctrine: `documentation/loop.playbook.md`. Read it before starting — this file
is the procedure, that file is the reasoning and the rules.

You are the **loop driver**. You own the iteration state, the stopping decision,
and the report. You delegate review and documentation; you never delegate the
stopping decision.

## Arguments

| Argument | Default | Meaning |
|----------|---------|---------|
| `UC<nn>` | required | Use case to drive, e.g. `UC06` |
| `--max-iterations=<n>` | `10` | Hard ceiling (`loop.playbook.md` § 4.1) |
| `--dry-run` | off | Do steps 0–2 and step 6's evaluation, then stop and report the plan. Writes the scoreboard; writes no code. |

---

## Step 0 — Setup (once)

1. Read `CLAUDE.md` for the authority order.
2. Read the always-read four: `architecture.definition.md`,
   `coding-style.definition.md`, `modelling.definition.md`, `tdd.definition.md`.
3. Resolve the spec: `documentation/use-cases/uc<nn>-*.spec.md`.
   Glob it — do not assume the filename stem, and note that spec filenames are
   the traceability key (`file-naming.definition.md`).
4. Confirm `git status` is clean enough to attribute a diff to this loop. If
   there are unrelated staged changes, say so before starting.
5. Initialise state:

```
iteration        = 0
max_iterations   = <arg>
open_findings    = []          # DRIFT findings carried forward
dod_history      = []          # ticked-count per iteration, for the no-progress detector
```

### Preflight gates — refuse to start if any fails

- **No spec** → stop. `sdd.playbook.md` § 1: no orphan code. Offer to write it.
- **No `## 10. Definition of Done`** → stop. Adding one is a Spec Phase change
  (`execution.playbook.md` § 3.2); do that first, explicitly, then re-invoke.
- **A criterion that is not objectively checkable** → stop and name it. "Code is
  clean" is not a DoD item (`sdd.playbook.md` § 4.2).
- **Status is `SUPERSEDED`** → stop. Superseded specs are not loop targets;
  point at the specs that replaced it.

Refusing to start is a correct outcome. A loop with a vague exit condition does
not terminate, it wanders.

---

## Step 1 — Gate Read (every iteration)

Re-read `## 10. Definition of Done` **from the spec, every iteration**. Do not
cache it: `spec-documenter` edits that file as part of the loop, and the spec is
authoritative (`loop.playbook.md` § 1).

Rebuild the mirror in `tasks.md`:

```markdown
## DoD Scoreboard – UC06 (iteration 3/10)

Mirrored from documentation/use-cases/uc06-mark-booking-active.spec.md § 10.
Authoritative source is the spec; this table is rebuilt each iteration.

- [x] AC-01 covered by TourBookingTest.should_complete_when_active
- [x] AC-02 covered by TourBookingTest.should_reject_completion_when_not_active
- [ ] MarkBookingCompletedDriver orchestration covered by MarkBookingCompletedDriverTest
- [ ] api/uc02-confirm-tour-booking.http covers 200, 404, 409
- [ ] ddd-hex-reviewer: PASS
- [ ] Quality gates (test.definition.md § 7) green
```

Compute `ticked / total` and append to `dod_history`.

---

## Step 2 — Select exactly one criterion

Priority (`loop.playbook.md` § 2.2):

1. any entry in `open_findings` — **always first**
2. domain-layer criteria
3. use-case-layer criteria
4. adapter / REST criteria
5. gate-shaped criteria — never selected; they are evaluated in step 6

State the selection explicitly before doing any work: *"Iteration 3/10 —
working: `<criterion>`"*.

If `--dry-run`, stop here and report.

---

## Step 3 — Inner loop: RED → GREEN → REFACTOR

`execution.playbook.md` § 3.4, governed by `tdd.definition.md`.

### RED

Pick the layer from `tdd.definition.md` § 3 (inside-out: domain → driver → rest
→ persistence). Write **one** test. Run it:

```bash
./gradlew :app:test --tests '*<TestClass>.<method>'
```

**Quote the actual failure output** — command, test name, assertion message.
This is a gate, not a formality (`tdd.definition.md` § 2). No quoted RED, no
GREEN. A predicted failure is not a failure.

If the test unexpectedly **passes**: that is a finding. Either the behaviour
already exists — close the criterion and note it — or the test does not test what
it claims. Do not proceed as if RED happened.

### GREEN

Least production code that passes that one test. Nothing more — do not implement
criteria that are not currently red.

```bash
./gradlew :app:test
```

Exit: new test passes, nothing previously green broke.

### REFACTOR

Improve naming, extract value objects, move logic to where the ontology says it
belongs. Exit: full suite green and **no test file modified**. If a test had to
change, the behaviour changed — back it out and do it as its own RED.

---

## Step 4 — Fan out (parallel, single message, two tool calls)

Dispatch both subagents concurrently. Do not serialise them; they are independent.

```
Agent(subagent_type: "ddd-hex-reviewer",
      prompt: "Review the working diff for UC<nn> iteration <n>. Criterion worked:
               <criterion>. Layers touched: <layers>. Return PASS or DRIFT.")

Agent(subagent_type: "spec-documenter",
      prompt: "Document UC<nn> iteration <n>. Criterion completed: <criterion>.
               Files changed: <paths>. Reconcile specs, the files in api/, and the
               tasks.md DoD scoreboard.")
```

---

## Step 5 — Resolve

- `PASS` → the criterion may be ticked if its evidence exists.
- `DRIFT` → **do not tick the criterion**, even if the behaviour works. Append
  every finding to `open_findings`; they lead the next iteration.
- `spec-documenter` reported `Conflicts` (code contradicts spec) → resolve before
  ticking anything the conflict touches. Decide which side is wrong and fix that
  side. Never resolve it by editing the spec to match code that is wrong.
- `spec-documenter` reported `Gaps` (missing spec) → Spec Phase work; add it to
  the queue.

Drift is never traded away for progress (`loop.playbook.md` § 2.5).

---

## Step 6 — Re-evaluate and check the stops

Recompute the DoD state **from evidence**, not from memory. For gate-shaped
criteria, run them:

```bash
./gradlew clean test
./gradlew build
```

Update `tasks.md`. Append the new ticked-count to `dod_history`.

### Exit — all three required (`loop.playbook.md` § 3)

- every DoD box ticked, with named evidence, **and**
- `ddd-hex-reviewer` returned `PASS` on the final state, **and**
- the `test.definition.md` § 7 gates pass, evaluated fresh

→ report `DONE`.

### Hard stops — check in this order

| Check | Condition | Terminal state |
|-------|-----------|----------------|
| ADR halt | any `sdd.playbook.md` § 6 trigger fired | `HALTED (ADR required)` |
| No progress | last two entries of `dod_history` are equal | `BLOCKED` |
| Max iterations | `iteration >= max_iterations` | `HALTED (max-iterations)` |

**No-progress detector** — the most important guard (`loop.playbook.md` § 4.2).
Two consecutive iterations with no box moving from unticked to ticked stops the
loop. Work that does not close a criterion does not count as progress; that is
deliberate. Report:

- the criterion that will not move
- what was attempted in both iterations
- most likely cause: under-specified criterion · missing spec · unmet dependency
  · not objectively checkable · genuine implementation obstacle

`BLOCKED` is a **success**. Surfacing an unsatisfiable criterion in two
iterations instead of ten is the job.

Never raise `max_iterations` from inside the loop. The caller decides.

Otherwise: `iteration += 1`, go to Step 1.

---

## Per-iteration report

```
Iteration      3/10
Criterion      MarkBookingCompletedDriver orchestration
Completed      MarkBookingCompletedDriver + inport triple
Verification   RED: MarkBookingCompletedDriverTest.should_publish_event_on_completion
                    → java.lang.AssertionError: Expecting actual not to be empty
               GREEN: 47 tests passed
               Gates: ./gradlew clean test OK, ./gradlew build OK
Specs touched  uc06-mark-booking-active.spec.md (§ 3, § 10),
               aggregate-tour-booking.spec.md (§ 4)
Drift review   PASS
DoD delta      2/6 → 3/6 (driver orchestration closed)
Next step      api/uc02-*.http coverage for 200/404/409
```

Final report adds the terminal state, the full scoreboard, and the last verdict.

---

## Anti-patterns

Each of these defeats the mechanism the loop exists to provide:

- Ticking a box without named evidence, or one a `DRIFT` finding touches
- **Editing the spec's DoD to make the loop exit.** If the DoD is wrong, fix it
  as a deliberate Spec Phase change and say so — never mid-flight to reach an exit
- Working several criteria in one iteration
- Raising `max-iterations` from inside the loop
- Re-running the reviewer to obtain a friendlier verdict
- Treating `BLOCKED` as failure and pushing on
- Deciding an ADR-level question to avoid halting
- Reporting the scoreboard from memory instead of recomputing it
- Skipping the RED quote because the failure was "obvious"
