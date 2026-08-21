# Tasks – Clean Baseline

Derived from `plan.md`. Replaces the previous `tasks.md`, which was the completed
ADR-0003 extraction checklist (51/51 ticked, four stale paths).

Governance: `execution.playbook.md` § 3 for each increment, `tdd.definition.md` for
RED → GREEN → REFACTOR, `loop.playbook.md` for the outer loop.

---

## DoD Scoreboard – Plan

Mirrored from `plan.md` § "Definition of Done for this plan". The plan is
authoritative; this is a scoreboard. **`plan.md` was rewritten** (it is now
"Alpine Booking — remaining work", Phases 0–6); the previous clean-baseline plan's
DoD closed 6/6 and is preserved in git history. Rebuilt against the new plan:

- [ ] Phase 0 committed, working tree clean
- [ ] `/loop-uc` has driven at least one increment end to end, defects fixed or written down
- [ ] The no-progress detector and the ADR halt both observed firing
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree with `Undocumented: none`
- [ ] `spec-documenter` reports no `Gaps` and no unresolved `Conflicts`
- [ ] `./gradlew clean test build` green; `app/data/` absent; every commit builds standalone
- [ ] Every `Class.method` citation in `documentation/` resolves to a test that exists (scripted)

## DoD Scoreboard – Implemented use cases

Mirrored from each use case spec's `## 10. Definition of Done`, recomputed from the
specs. Specs are authoritative.

UC01–UC07 fully closed (the baseline's last twelve were gate-shaped — six
`ddd-hex-reviewer: PASS` and six `Quality gates green` — and closed together at task 5.8
on a witnessed `PASS` verdict and a witnessed green run, not on predicted ones). UC08's
two remaining boxes are the same gate shape and close only on witnessed runs.

| Use case | Ticked | Open |
|----------|--------|------|
| UC01 RequestTourBooking | **15/15** | — |
| UC02 ConfirmTourBooking | **10/10** | — |
| UC03 CancelTourBooking | **11/11** | — |
| UC04 ChangeParticipants | **15/15** | — |
| UC05 StartTour | **16/16** | — |
| UC06 MarkBookingActive | **13/13** | — |
| UC07 MarkBookingCompleted | **21/21** | — |
| UC08 CancelBookingByUser | **24/26** | 2 — `ddd-hex-reviewer: PASS` and quality gates, both gate-shaped and awaiting a witnessed run |
| UC09 MarkBookingCancelledByGuide | **24/26** | 2 — gate-shaped (`ddd-hex-reviewer: PASS` post-rework, quality gates), awaiting witnessed runs. The 3 UC12-blocked boxes closed with UC12; the inport was reworked per-tour (`tourId` in, `cancelledCount` out) in the same increment |
| UC12 CancelTourByGuide | **22/24** | 2 — gate-shaped (`ddd-hex-reviewer: PASS`, quality gates), awaiting witnessed runs |

UC07 (Phase 6 feature work) was added after the baseline paragraph above was written and
is now closed at 21/21. Its two agent-reported contradictions were both real and were
resolved in opposite directions — one by correcting the spec, one by correcting the code
(UC07 § 10 Governance records which was which). Its `ddd-hex-reviewer: PASS` came on the
second pass; the first returned `DRIFT`.

**80/80.** Closed across the effort: UC01 AC-03/AC-04 REST boundary (3.2) · UC04 400 and
502 (3.3), persistence (3.4) · UC05 driver test (3.1), domain spec (1.1), port specs
(1.2, 1.3) · UC06 persistence (3.5), listener test (2.3) · and the twelve gate-shaped
boxes at 5.8.

Two of these were real bugs rather than coverage gaps: `TourBookingJooqRepository.update`
wrote only `status`, silently discarding UC04's entire effect; and the controller tests
booted the full application without the `test` profile, writing a file-based H2 database
that leaked state between runs.

---

---

## Completed — the August 2026 modernization

The 51-task list that drove Phases 0–6 of the previous `plan.md` is complete and lives in
git history (`git log --oneline main..HEAD`, 32 commits). Summarised rather than kept inline,
because it is a record of finished work and the detail is in the commit messages:

| Phase | What it did |
|-------|-------------|
| 0 Rulings | seven maintainer decisions, all obtained |
| 1 Documentation | guide domain + port specs written; eight documented-but-unfollowed rules scoped |
| 2 Architecture fixes | `AvailabilityUnavailableException` relocated; `shared.outport` adapters moved to `shared.outbound` with a new `SharedConfig` |
| 3 Test gaps | five closed, two of which were real bugs — `update` writing only `status`, and controller tests leaking a file-based H2 |
| 4 Naming | `GuideOperationsConfig`/`…ExceptionHandler` → `GuideConfig`/`GuideExceptionHandler`, per ADR-0003 |
| 5 Mechanical rules | ArchUnit (30 rules, in CI), Spotless, `build.yml` pinned to Temurin 25 |
| 6 Feature work | UC11, UC07, UC08, UC09, UC12 |

---

## Phase 0: Publish to the public showcase

Mostly manual — the workflow exists, the setup does not. Blocks nothing else, but it is what
makes the repository a showcase rather than a private project.

### 0.1 - Publish setup (manual, maintainer only)
- [ ] **Task 0.1.1**: Create `github.com/dominikgaller/alpine-booking-reference`. The workflow does not create it.
- [ ] **Task 0.1.2**: Create a **fine-grained** PAT, *Contents: read and write*, scoped to that repository only. Add it here as the Actions secret `SHOWCASE_PUBLISH_TOKEN`. Not a classic `repo` token — that grants write to every repository on the account in order to publish one.
- [ ] **Task 0.1.3**: Run the workflow with `dry_run: true` via *workflow_dispatch*. It filters, verifies and builds without pushing. This is what turns the pipeline from reasoned into verified — the `git-filter-repo` step has never been executed.
- [ ] **Task 0.1.4**: Read the dry-run log. Confirm the commit count is plausible and the guard reported clean.
- [ ] **Task 0.1.5**: Re-run without `dry_run`. Confirm the public repository has history, not one squashed commit.

### 0.2 - Make the force-push recoverable
- [ ] **Task 0.2.1**: Decide whether to add a timestamped tag alongside `main` in `publish-showcase.yml`, so a bad-but-guard-passing filter is recoverable from the public side. Currently it is not.
- [ ] **Task 0.2.2**: If yes: add the tag push, and re-run with `dry_run: true` first.

### 0.3 - Dangling references in the published copy
- [ ] **Task 0.3.1**: `CLAUDE.md` and `loop.playbook.md` reference `plan.md` and `tasks.md`, neither of which will exist publicly. Decide: reword the documents (recommended — they *are* private working material), strip references at publish time (fragile), or publish the two files after all.
- [ ] **Task 0.3.2**: Apply the decision. If rewording, it is a doctrine change → own commit, before anything depending on it (`file-usage.definition.md` § 5.1).

### 0.4 - README for the public repository
- [ ] **Task 0.4.1**: There is none. The showcase would land on an empty front page. Highest-value single thing to write before pointing anyone at the public repo — and it is the maintainer's voice, not an agent's.

---

## Phase 1: Finish what is in flight

The working tree carries UC12 plus the UC09 per-tour rework, **uncommitted**. Close this
before starting anything else.

### 1.1 - Close the review loop
- [ ] **Task 1.1.1**: Read the fourth `ddd-hex-reviewer` verdict on the increment. Passes one to three returned `DRIFT`; every finding is addressed.
- [ ] **Task 1.1.2**: If `DRIFT`, fix and re-dispatch. If `PASS`, tick the `ddd-hex-reviewer: PASS` box in UC09 § 10 and UC12 § 10 — and only then.
- [ ] **Task 1.1.3**: Run `./gradlew clean test` and `./gradlew build`; record the witnessed figure and tick both quality-gate boxes. Do not tick from a remembered run — UC09's previous tick predated the rework and had to be reverted.

### 1.2 - Commit
- [ ] **Task 1.2.1**: Split the commit. The `guide` `CancellationReason` fix is a domain change driven by a review finding; `publish-showcase.yml` is infrastructure; UC12 + the UC09 rework is the feature increment.
- [ ] **Task 1.2.2**: For each commit, run `git diff --cached --name-only` before committing. One earlier commit bundled a staged deletion from an unrelated `git rm` and did not compile.
- [ ] **Task 1.2.3**: Verify each commit builds standalone: `git stash -u && ./gradlew clean build && git stash pop` at each step.
- [ ] **Task 1.2.4**: Tick the Phase 1 boxes in `plan.md`.

---

## Phase 2: Prove the harness actually runs

**Highest value in the plan.** `/loop-uc` has never driven an increment; every one so far was
the inner loop by hand.

### 2.1 - First real `/loop-uc` run
- [ ] **Task 2.1.1**: Pick a small increment. Block 4.1 below is the best candidate — one ArchUnit rule, one plantable violation, exactly one RED → GREEN → REFACTOR turn.
- [ ] **Task 2.1.2**: Write a use-case-shaped spec for it if the loop needs one to parse a DoD from. Finding out that it *does* is itself a result worth recording.
- [ ] **Task 2.1.3**: Run `/loop-uc`. Do not intervene except to stop it.
- [ ] **Task 2.1.4**: Write down every defect. A loop that has never run will have them; they are the deliverable of this phase, and each is a better demo artefact than a green run.
- [ ] **Task 2.1.5**: Fix the defects, or record them in `loop.playbook.md` as known limits.

### 2.2 - Prove the guards fire
- [ ] **Task 2.2.1**: Point the loop at a deliberately unsatisfiable DoD criterion. Confirm it reports `BLOCKED` after two iterations instead of burning tokens. This is the guard that makes an autonomous loop safe to demo live and it has never been triggered.
- [ ] **Task 2.2.2**: Give it an increment that trips an `sdd.playbook.md` § 6 ADR trigger. Confirm it halts and asks rather than deciding.
- [ ] **Task 2.2.3**: Confirm the max-iterations stop works.

### 2.3 - Decide what the talk claims
- [ ] **Task 2.3.1**: Decide honestly whether `/loop-uc` earns its place, or whether the inner loop plus the two agents is the real story and the outer loop is scaffolding. Either answer is fine; an unexercised skill presented as working is not.
- [ ] **Task 2.3.2**: Run `/uc-to-plan` and `/execute-task` once each, or delete them. A command that does not work is worse than no command. (`/plan-to-task` has been exercised.)

---

## Phase 3: Doctrine debt

Rules that have already governed increments while living only in a use-case spec. Each is a
doctrine-only commit, landing **before** any code that relies on it.

### 3.1 - Attribution in the event type
- [ ] **Task 3.1.1**: Write the rule into `modelling.definition.md` § Domain Event: distinct event types per business fact rather than one event with a discriminator. Include the N=2-is-fine / revisit-at-N=3 note and `BookingCancelledByUser`/`ByGuide` as the worked example.
- [ ] **Task 3.1.2**: Doctrine-only commit; verify it builds standalone.

### 3.2 - When an aggregate method may branch on its caller
- [ ] **Task 3.2.1**: `TourBooking.cancel` varies its state guard *and* its idempotency by `CancelledBy`; `GuideTour.cancel` is not idempotent while its booking-side counterpart is. Each is defensible and documented; no rule says when caller-dependent behaviour is modelling and when it is two methods wearing one name. Write it down.

### 3.3 - Does § 11 rule 3 bind test code?
- [ ] **Task 3.3.1**: `ContextRegistryTest` uses `DO_NOT_INCLUDE_TESTS`, encoding "production only", but nothing states it. `CancelTourByGuideIT` imports seven `booking` types from inside the `guide` package to seed fixtures, and could have gone through `booking`'s inport. Answer it in `architecture.definition.md` § 11 or `test.definition.md`.
- [ ] **Task 3.3.2**: If tests are bound, rework `CancelTourByGuideIT`'s seeding through the inport. If not, say so explicitly so the next reviewer does not re-raise it.

### 3.4 - Where an integration-failure exception lives
- [ ] **Task 3.4.1**: `BookingCancellationFailedException` sits in `guide.core.domain.guidetour.exception`, which § 3 defines as "domain exceptions for this aggregate" — and it is not a `GuideTour` rule violation. The placement is forced, because § 6 rule 3 lets `inbound.rest` import only from that package. Doctrine leaves no compliant alternative, which is the gap.

### 3.5 - Must a rule citation resolve?
- [ ] **Task 3.5.1**: Six places cited `architecture.definition.md` § 6 rule 2 for a rule that section does not contain; the reasoning was right and the citation was not. Nothing requires a citation to resolve to the section it names. Decide whether that is a rule, and if so how it is checked.

### 3.6 - Command timestamp modelling
- [ ] **Task 3.6.1**: `guide`'s `StartTourCommand` and `CompleteTourCommand` use `Optional<Instant>`; every `booking` command uses a nullable `Instant` resolved at the driver. Both cannot be right. `coding-style.definition.md` § 1.4's exception argues for nullable.
- [ ] **Task 3.6.2**: RED — a test asserting the chosen shape on one command. GREEN — conform the other context. REFACTOR.
- [ ] **Task 3.6.3**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [ ] **Task 3.6.4**: `./gradlew clean test` and `./gradlew build`.

### 3.7 - Missing `@throws`
- [ ] **Task 3.7.1**: `CancelTourBookingUseCase` omits `InvalidBookingRequestException` although `CancellationReason` throws it; `MarkBookingCancelledByGuideUseCase` inherited the omission (§ 7.1/§ 7.3).

---

## Phase 4: Turn conventions into enforcement

Rules the codebase follows with nothing stopping it from stopping. Each block is one
ArchUnit rule with a plantable violation — the best `/loop-uc` candidates for Phase 2.

### 4.1 - § 8.1's REST half
- [ ] **Task 4.1.1**: RED — plant an `Instant` component on a command reachable from `inbound.rest` and confirm nothing fails today.
- [ ] **Task 4.1.2**: GREEN — an ArchUnit rule: no command referenced from `inbound.rest` may declare an `Instant` component.
- [ ] **Task 4.1.3**: REFACTOR — remove the planted violation; confirm the rule still passes and the suite is green.
- [ ] **Task 4.1.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [ ] **Task 4.1.5**: `./gradlew clean test` and `./gradlew build`.

### 4.2 - `reconstitute` overload selection
- [ ] **Task 4.2.1**: `ClassRoleRulesTest` constrains which *classes* may call `reconstitute`, not which *overload*, so a mapper could reach for the short form and silently drop cancellation attribution. Both aggregates' Javadoc says so honestly; the persistence ITs are what catch it. Decide: mechanise, or record as accepted with the ITs named as the control.

### 4.3 - Cross-context propagation
- [ ] **Task 4.3.1**: RED — set `MarkBookingCancelledByGuideDriver` to `REQUIRES_NEW` and confirm no test fails. This is § 10 condition 3 going unenforced.
- [ ] **Task 4.3.2**: GREEN — an ArchUnit rule, or the Phase 5.4 test, whichever actually catches it.
- [ ] **Task 4.3.3**: Dispatch both agents; run the gates.

---

## Phase 5: Test-depth gaps

### 5.1 - ADR-0002's guarantee is unverified
- [ ] **Task 5.1.1**: No integration test asserts `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` for the UC06/UC07 listeners, so "a rollback cannot leak an event" is verified nowhere. Both listener tests document the omission honestly.
- [ ] **Task 5.1.2**: RED — a `@SpringBootTest` that rolls back a guide-tour transaction and asserts no booking was activated. Use `CancelTourByGuideRollbackIT` as the template.
- [ ] **Task 5.1.3**: GREEN if it fails; if it passes first try, mutation-verify before believing it.
- [ ] **Task 5.1.4**: Dispatch both agents; run the gates.

### 5.2 - Context-loads smoke test
- [ ] **Task 5.2.1**: `CancelTourByGuideIT` and `CancelTourByGuideRollbackIT` are the only tests that boot the whole application, so a broken bean graph is invisible outside them. Add one cheap `@SpringBootTest` that only asserts the context starts.

### 5.3 - Per-batch rollback
- [ ] **Task 5.3.1**: Nothing proves booking 7 failing rolls back bookings 1–6 in UC09's fan-out. RED, then GREEN.

### 5.4 - The rollback IT's blind spot
- [ ] **Task 5.4.1**: `CancelTourByGuideRollbackIT` mocks the booking inport, so it proves `guide`'s writes roll back but never `booking`'s. Switching the callee to `REQUIRES_NEW` would break § 10 condition 3 and **no test would fail**.
- [ ] **Task 5.4.2**: RED — a test where the real callee succeeds on booking 1 and fails on booking 2, asserting booking 1 is still CONFIRMED. `ddd-hex-reviewer` called this the most valuable test left in the repository.
- [ ] **Task 5.4.3**: Dispatch both agents; run the gates.

---

## Phase 6: Domain gaps (deliberately last)

Real, documented, and not worth doing for the demo unless a specific talk needs them. Each
is a Known Gap in the relevant port spec, which is itself the point.

### 6.1 - Optimistic locking
- [ ] **Task 6.1.1**: Write the ADR (persistence strategy, `sdd.playbook.md` § 6 item 4) and **wait for approval**. This one would make a good ADR-writing demo in its own right.
- [ ] **Task 6.1.2**: If approved: migration, version column, RED test proving the lost update first.

### 6.2 - Read side
- [ ] **Task 6.2.1**: `outbound.persistence.read` is in the package ontology with nothing in it; every use case is a command, so half the CQRS structure the architecture describes is never exercised. One query use case would light it up. Spec first.

### 6.3 - Outbox
- [ ] **Task 6.3.1**: The UC06/UC07 fan-outs are single `REQUIRES_NEW` transactions with no retry. `architecture.definition.md` § 10 records this as accepted, with reasoning. Revisit only if a talk needs it.

### 6.4 - Open note
- [ ] **Task 6.4.1**: `notes.md` still asks whether an aggregate should hold its own `List<DomainEvent>`.

---

## Sequencing

Phase 1 blocks everything — do not start new work on an uncommitted tree.

Phase 0 is independent and mostly manual; it can happen in parallel with anything.

Phase 2 depends on Phase 4.1 existing as a candidate increment, so read 4.1 before starting
2.1. Everything in Phases 3–6 is independent of everything else, which is deliberate: after
five months away, the useful property is being able to pick any single block and finish it.
