# Loop Playbook – Loop-Driven Development

## Purpose

`execution.playbook.md` defines the **inner loop**: one increment, from Read
Phase to Closeout. It runs once and reports.

This playbook defines the **outer loop**: repeat the inner loop until a use case
is genuinely finished, where "finished" is not a judgement call but a condition
read out of the use case's own specification.

The point of loop-driven development is to move the stopping decision out of the
agent and into the spec. An agent asked "are you done?" will eventually say yes.
An agent asked "are all nine DoD boxes ticked?" cannot.

Executable entry point: `.claude/skills/loop-uc/SKILL.md`, invoked as
`/loop-uc UC05`.

---

# 1. The Exit Condition Is the Spec

Every use case spec carries a `## 10. Definition of Done` — a checkbox list of
objectively checkable criteria (`sdd.playbook.md` § 4.2). That list, and nothing
else, decides when the loop stops.

```
documentation/use-cases/uc05-issue-individual-leasing-contract.spec.md
  ## 10. Definition of Done      ← authoritative
        │
        │ mirrored each iteration
        ▼
  tasks.md  ## DoD Scoreboard – UC05   ← working copy, rebuilt from the spec
```

The spec is authoritative. `tasks.md` is a scoreboard for humans watching the
loop run. Where they disagree, the spec wins and the mirror is rebuilt — never
the reverse.

**A DoD criterion may only be ticked when a named artefact proves it**: a passing
test cited by name, an existing file, a `PASS` verdict, a green gate. Ticking a
box because the work "feels done" defeats the entire mechanism, and it is the
one failure mode this playbook exists to prevent.

---

# 2. One Iteration

```
   ┌──────────────────────────────────────────────────────────────┐
   │                                                              │
   ▼                                                              │
 1. GATE READ      parse DoD from spec → mirror into tasks.md     │
       │                                                          │
 2. SELECT         highest-priority unmet criterion (exactly one)  │
       │                                                          │
 3. INNER LOOP     execution.playbook.md § 3.4: RED → GREEN → REFACTOR
       │                                                          │
 4. FAN OUT        ddd-hex-reviewer  ‖  spec-documenter           │
       │                                                          │
 5. RESOLVE        DRIFT? → finding becomes next iteration's work  │
       │                                                          │
 6. RE-EVALUATE    recompute DoD state; update scoreboard          │
       │                                                          │
       └── all three exit conditions met? ──no──────────────────────┘
                    │ yes
                    ▼
                  DONE
```

## 2.1 Gate Read

Locate `documentation/use-cases/uc<nn>-*.spec.md` and parse
`## 10. Definition of Done`.

- If the section is missing, the loop **does not start**. Add the DoD to the spec
  first (this is a Spec Phase activity, `execution.playbook.md` § 3.2) and say so.
- If a criterion is not objectively checkable, the loop does not start. Fix the
  spec.
- Mirror the parsed list into `tasks.md` under a `## DoD Scoreboard – UC<nn>`
  heading, rebuilt from the spec each iteration.

## 2.2 Select

Choose **exactly one** unmet criterion per iteration. Priority order:

1. Any open `DRIFT` finding from the previous iteration — always first.
   Architecture debt compounds; behaviour debt does not.
2. Domain-layer criteria (invariants, state transitions) — the inside-out
   ordering of `tdd.definition.md` § 3.
3. Use-case-layer criteria (orchestration, events, failure paths).
4. Adapter, schema and persistence criteria.
5. Gate-shaped criteria (`ddd-hex-reviewer: PASS`, build green) — these are
   *consequences*, evaluated in step 6, never "worked on" directly.

One criterion per iteration is a real constraint. Batching them reintroduces the
untested-branch problem that `tdd.definition.md` § 1.2 exists to prevent.

**A cross-context use case still gets one criterion per iteration.** UC04 and UC05
each touch two contexts, and the temptation is to do "the whole interaction" in one
pass. Split by aggregate: the master-contract criterion and the individual-contract
criterion are separate iterations even though one calls the other.

## 2.3 Inner Loop

Run `execution.playbook.md` § 3.4 for the selected criterion: RED → GREEN →
REFACTOR, with the quoted RED failure required before any production code
(`tdd.definition.md` § 2).

If the iteration reveals that an ADR trigger fires (`sdd.playbook.md` § 6), the
loop **halts** and asks the user. It does not decide architecture on its own.

## 2.4 Fan Out

Dispatch both subagents **in parallel** on the working diff
(`execution.playbook.md` § 3.5):

| Agent | Model | Returns |
|-------|-------|---------|
| `ddd-hex-reviewer` | opus | `PASS` or `DRIFT` + `file:line` findings |
| `spec-documenter` | fable | reconciled specs, `graphql/*.graphql`, scoreboard refresh |

They are independent. Neither waits for the other.

## 2.5 Resolve

- **`PASS`** → the criterion may be ticked, if its evidence exists.
- **`DRIFT`** → the criterion is **not** ticked, even if the behaviour works.
  Each finding becomes a work item at the front of the next iteration's queue.
- **`Conflicts` from `spec-documenter`** (code contradicts spec) → resolve
  explicitly before ticking anything the conflict touches. Decide which side is
  wrong and fix that side. Never resolve it by editing the spec to match code
  that is wrong.

**Drift is never traded away for progress.** There is no iteration budget, no
deadline and no partial credit that justifies ticking a box a `DRIFT` finding
touches. An architecture violation that ships is worse than a use case that
does not.

## 2.6 Re-evaluate

Recompute the full DoD state from evidence — do not carry ticks forward on
trust. Update the scoreboard. Record the iteration's DoD delta; the no-progress
detector (§ 4) needs it.

---

# 3. Exit Conditions

The loop exits successfully only when **all three** hold simultaneously:

1. **Every DoD box in the spec is ticked**, each with named evidence.
2. **`ddd-hex-reviewer` returns `PASS`** on the final state.
3. **The quality gates in `test.definition.md` § 7 pass** — the canonical list,
   evaluated fresh, not remembered from an earlier iteration.

Two of three is not done. In particular, green tests with an open `DRIFT` finding
is not done, and a `PASS` verdict on an incomplete DoD is not done.

**A gate evaluated with Postgres absent has not been evaluated.** If the use case
touched persistence and every `*IT` skipped, condition 3 is unmet regardless of the
green tick (`test.definition.md` § 2.3). Start the database and re-run.

---

# 4. Hard Stops

An unbounded loop is a runaway process, not autonomy. Three stops, all mandatory.

## 4.1 Max iterations

Default **10**. On reaching it, stop and report `HALTED (max-iterations)` with
the DoD state and what remains. Never silently raise the ceiling — the caller
decides whether to continue.

## 4.2 No-progress detector

**Two consecutive iterations with no DoD delta → stop and report `BLOCKED`.**

This is the most important guard in this playbook. Without it, an
under-specified or impossible criterion causes the loop to re-attempt the same
work indefinitely, burning tokens while appearing busy. A loop that cannot
recognise its own futility is not a loop, it is a leak.

"No delta" means no box moved from unticked to ticked. Progress that does not
move a box does not count — that is the point. If real work is happening but no
criterion ever closes, the criteria are wrong, and the correct action is to stop
and say so.

The `BLOCKED` report must name:

- the criterion that will not move
- what was attempted, in both iterations
- the most likely cause: under-specified criterion · missing spec · unmet
  dependency · criterion not objectively checkable · genuine implementation
  obstacle · **environment** (Postgres unavailable, so a persistence criterion
  cannot be evidenced)

`BLOCKED` is a **successful** outcome for the loop. Surfacing an unsatisfiable
criterion in two iterations is exactly the job.

## 4.3 ADR halt

Any ADR trigger (`sdd.playbook.md` § 6) stops the loop and asks the user.
Architectural decisions are never made mid-loop. This is not a failure state
either — it is the governance model working.

---

# 5. Model Routing

Loop stages differ enormously in how much reasoning they need. Routing them to
one tier wastes capability on bookkeeping or starves the reasoning that matters.

| Stage | Model | Why |
|-------|-------|-----|
| Gate read, scoreboard mirroring | fable | Mechanical parse-and-write |
| Select | inherit | Small decision, needs loop context |
| RED — designing the failing test | opus | The hardest step: what the domain *must* do |
| GREEN — minimal implementation | opus | Domain modelling decisions |
| REFACTOR | inherit | |
| `ddd-hex-reviewer` | opus | Adversarial judgement; anemic-model detection is not grep |
| `spec-documenter` | fable | High-volume mechanical reconciliation |
| Re-evaluate, hard-stop checks | inherit | Cheap; must not be delegated away from the loop driver |

Do not route the drift reviewer to a cheaper tier to save budget. A reviewer that
misses drift is worse than no reviewer, because it produces a `PASS` the loop
then trusts.

---

# 6. Reporting

Each iteration reports in the `execution.playbook.md` § 5 contract, plus:

```
Iteration      – n of max
Criterion      – the one DoD item selected
DoD delta      – x/y → x'/y ticked, naming what closed
```

The final report states the terminal state — `DONE`, `BLOCKED`, `HALTED
(max-iterations)`, or `HALTED (ADR required)` — the full DoD scoreboard, and the
last drift verdict.

---

# 7. Anti-Patterns (Strictly Forbidden)

- Ticking a DoD box without named evidence
- Ticking a box a `DRIFT` finding touches
- Ticking a persistence box on a run where the `*IT` skipped
- Editing the spec's DoD to make the loop exit — if the DoD is wrong, fix it as a
  deliberate Spec Phase change and say so; never mid-flight to reach an exit
- Working several criteria in one iteration
- Raising `max-iterations` from inside the loop
- Suppressing or re-running the reviewer to obtain a friendlier verdict
- Treating `BLOCKED` as failure and pushing on regardless
- Deciding an ADR-level question to avoid halting
- Reporting the scoreboard from memory instead of recomputing from evidence

Spec-driven. Test-driven. Drift-blocked. Bounded.
