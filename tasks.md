# Tasks – Clean Baseline

Derived from `plan.md`. Replaces the previous `tasks.md`, which was the completed
ADR-0003 extraction checklist (51/51 ticked, four stale paths).

Governance: `execution.playbook.md` § 3 for each increment, `tdd.definition.md` for
RED → GREEN → REFACTOR, `loop.playbook.md` for the outer loop.

---

## DoD Scoreboard – Plan

Mirrored from `plan.md` § "Definition of Done for this plan". The plan is
authoritative; this is a scoreboard.

- [x] `ddd-hex-reviewer` returns `PASS` on the full tree, `Undocumented: none`
- [x] `spec-documenter` reports no `Gaps`; its remaining `Conflicts` were the three stale Javadocs, now fixed
- [x] Every UC01–UC06 DoD box ticked with named evidence — 80/80
- [x] `Undocumented` is empty
- [x] ArchUnit enforces § 6 and § 11, in CI, 30 rules
- [x] `./gradlew clean test build` green, 187 tests, `app/data/` absent

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

## Phase 0: Rulings

No code. Each block is a decision that is the user's to make; several later blocks
are blocked on them. Recommendations are in `plan.md` Phase 0.

### 0.1 - U1 - Where `shared.outport` adapters live
- [x] **Task 0.1**: Obtain the ruling. Recommendation: `shared.outbound.*`, wired by a new `bootstrap/SharedConfig`. **Blocks 1.5 and 2.4.**

### 0.2 - U2 - One-aggregate-per-transaction
- [x] **Task 0.2**: Obtain the ruling. Recommendation: document as a guideline, not a rule; leave `TourStartedListener`'s fan-out as-is. **Blocks 1.5 and 2.3.**

### 0.3 - U3 - Auditable ordering for doctrine changes
- [x] **Task 0.3**: Obtain the ruling. Recommendation: a `*.definition.md` / `*.playbook.md` change lands in its own commit, before any code relying on it. **Blocks 1.5.** Adopt before Phase 2, which changes rules and code together.

### 0.4 - U4 - RED rule for behaviour-preserving refactors
- [x] **Task 0.4**: Obtain the ruling. `tdd.definition.md` § 2 gates all production code on a quoted RED failure, but a pure relocation or rename cannot produce one — there is no new behaviour to fail on. Blocks 2.1, 2.2, 2.4 and all of Phase 4 are exactly this shape. Recommendation: add a clause — for a change with no behavioural delta, the evidence is the *unchanged* suite (identical test method count, all green before and after), stated explicitly in the report. **Blocks 1.5, 2.1, 2.4, Phase 4.**

### 0.5 - E1 - `coding-style.definition.md` § 3.2
- [x] **Task 0.5**: Obtain the ruling on the dead layering section (`domain / application / in / out / adapter.*`, which no code follows). Recommendation: delete it, replace with a pointer to `architecture.definition.md` § 3. **Blocks 1.4.**

### 0.6 - E4/B1/B2 - ADR-0003 naming divergence
- [x] **Task 0.6**: Choose one — (a) rename `GuideOperationsConfig`/`GuideOperationsExceptionHandler` to match ADR-0003 and `architecture.definition.md` § 3 (recommended), (b) write a superseding ADR blessing the current names, (c) record as accepted historical drift. **Gates all of Phase 4.**

### 0.7 - G1 - Java baseline
- [x] **Task 0.7**: **Done.** Java 25 LTS, Temurin pinned, `adr/0006-java-25-baseline.adr.md`.

---

## Phase 1: Documentation

No production code. 1.1–1.3 have no dependency on Phase 0 and can start immediately.

### 1.1 - Guide domain spec (D1)
- [x] **Task 1.1.1**: Read `GuideTour`, `GuideTourStatus`, the three guide exceptions and `GuideTourTest` to recover the actual invariants and state model.
- [x] **Task 1.1.2**: Write `documentation/domain/aggregate-guide-tour.spec.md` from `domain.spec.template.md` — SCHEDULED → RUNNING → FINISHED/CANCELLED, `start(Instant)` pre/postconditions, `TourStarted` emission, consistency boundary.
- [x] **Task 1.1.3**: Include the transitions UC11 and UC12 specify (`complete`, `cancel`) as **not yet implemented**, so the spec does not imply they exist.
- [x] **Task 1.1.4**: Tick the UC05 DoD box for the domain spec; refresh this scoreboard.
- [x] **Task 1.1.5**: Dispatch `spec-documenter` to verify the spec against disk, and `ddd-hex-reviewer`.
- [x] **Task 1.1.6**: Run `./gradlew clean test` and `./gradlew build`.

### 1.2 - Guide repository port spec (D2)
- [x] **Task 1.2.1**: Write `documentation/ports/guide-tour-repository.outport.spec.md` following the shape of the five existing port specs (Purpose, 1. Interface, 2. Method Contracts, 3. Transaction Boundary, 4. Reference Implementation, 5. Constraints).
- [x] **Task 1.2.2**: Record `findById` / `save` / `update` contracts as actually implemented by `GuideTourJooqRepository`, including the UTC `LocalDateTime` mapping that `GuideTourJooqRepositoryIT.save_persistsScheduledStart_asUtcLocalDateTime` pins.
- [x] **Task 1.2.3**: Tick the UC05 DoD box for the port spec; refresh this scoreboard.
- [x] **Task 1.2.4**: Dispatch `spec-documenter` and `ddd-hex-reviewer`.
- [x] **Task 1.2.5**: Run `./gradlew clean test` and `./gradlew build`.

### 1.3 - StartTour inport spec (D3)
- [x] **Task 1.3.1**: Write `documentation/ports/start-tour.inport.spec.md`, mirroring `request-tour-booking.inport.spec.md`.
- [x] **Task 1.3.2**: Dispatch `spec-documenter` and `ddd-hex-reviewer`.
- [x] **Task 1.3.3**: Run `./gradlew clean test` and `./gradlew build`.

### 1.4 - Retire the dead layering section (E1) — blocked on 0.5
- [x] **Task 1.4.1**: Apply the 0.5 ruling to `coding-style.definition.md` § 3.2.
- [x] **Task 1.4.2**: Grep for other references to the `domain / application / in / out / adapter` vocabulary and remove or redirect them.
- [x] **Task 1.4.3**: Dispatch `ddd-hex-reviewer`; confirm its `Undocumented` list no longer names § 3.2.

### 1.5 - Write the rulings down (U1–U4) — blocked on 0.1–0.4
- [x] **Task 1.5.1**: U1 → `architecture.definition.md` § 9: where adapters for `shared.outport` live, and how they are wired.
- [x] **Task 1.5.2**: U2 → `architecture.definition.md` § 10 or § 4.8: the one-aggregate-per-transaction position, as guideline or rule per the ruling.
- [x] **Task 1.5.3**: U3 → `sdd.playbook.md` § 5 Change Protocol: doctrine changes land in their own commit before dependent code.
- [x] **Task 1.5.4**: U4 → `tdd.definition.md` § 2: the behaviour-preserving-refactor clause and its evidence requirement.
- [x] **Task 1.5.5**: Update `.claude/agents/ddd-hex-reviewer.md` so its checklist enforces the four new rules and its `Undocumented` section stops reporting them.
- [x] **Task 1.5.6**: Commit this block **on its own**, before Phase 2 — the first application of the U3 rule.

### 1.6 - Prune stale documentation (E2, E5)
- [x] **Task 1.6.1**: `notes.md` — remove the guide-domain-spec gap (closed by 1.1) and the DomainEvent-list musing if 0.x settled it; keep the read-model note.
- [x] **Task 1.6.2**: Confirm no document still references `plan.md`/`tasks.md` as the ADR-0003 extraction.
- [x] **Task 1.6.3**: Dispatch `spec-documenter`; expect no `Gaps`.

### 1.7 - Tooling hygiene (G6, G7)
- [x] **Task 1.7.1**: Add `app/data/` to `.gitignore` — belt-and-braces; `test.definition.md` § 1.3 already forbids the condition that creates it.
- [x] **Task 1.7.2**: Remove the stray `Bash(test:*)` from `.claude/settings.local.json` (matches the shell builtin, not Gradle).
- [x] **Task 1.7.3**: Confirm `./gradlew clean test` leaves no `app/data/`.

---

## Phase 2: Architecture fixes

Order matters: 2.1 before 2.2. Blocks 2.1, 2.2 and 2.4 are behaviour-preserving —
follow the U4 ruling from 0.4 for what stands in for RED.

### 2.1 - Move `AvailabilityUnavailableException` to the inport surface (A2) — blocked on 0.4
- [x] **Task 2.1.1**: Baseline evidence — run `./gradlew clean test`, record the test count (currently 124) and that it is green. Per U4 this is the RED substitute; there is no behavioural delta.
- [x] **Task 2.1.2**: Move `AvailabilityUnavailableException` from `booking.core.outport` to `booking.core.domain.tourbooking.exception`, the home `architecture.definition.md` § 4.2 permits a usecase interface to reference.
- [x] **Task 2.1.3**: Update imports in `RequestTourBookingUseCase`, `ChangeParticipantsUseCase`, the drivers, `BookingExceptionHandler`, `StubAvailabilityChecker`, `AvailabilityChecker` and the affected tests.
- [x] **Task 2.1.4**: REFACTOR — check whether `AvailabilityChecker`'s contract should declare it, and whether the exception belongs to the aggregate or to a shared `booking` exception package.
- [x] **Task 2.1.5**: Confirm the suite is unchanged — same test count, all green, no test modified beyond imports.
- [x] **Task 2.1.6**: Update `ports/availability-checker.outport.spec.md` § 5 and UC01/UC04 § 3 exception tables.
- [x] **Task 2.1.7**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 2.1.8**: Run `./gradlew clean test` and `./gradlew build`.

### 2.2 - Confirm A1 dissolved
- [x] **Task 2.2.1**: Verify `BookingExceptionHandler` now imports only `core.inport` types and domain exceptions — the § 6.3 / § 4.5 violation was a symptom of 2.1.
- [x] **Task 2.2.2**: Verify `GuideOperationsExceptionHandler` has no equivalent violation.
- [x] **Task 2.2.3**: Dispatch `ddd-hex-reviewer`; expect both A1 and A2 gone from `Pre-existing`.

### 2.3 - Test then relocate the CONFIRMED decision (C2 + A3) — blocked on 0.2
- [x] **Task 2.3.1**: RED — add `TourStartedListenerTest` pinning current behaviour: a `TourStarted` event activates every CONFIRMED booking for the tour and skips others. Run it, quote the failure.
- [x] **Task 2.3.2**: GREEN — minimal wiring so the test passes against today's implementation. This block starts by *covering* untested code, so the first cycle may go green immediately; state that plainly if so.
- [x] **Task 2.3.3**: RED — add a test asserting the listener no longer pre-filters on status: a CANCELLED booking must be attempted and no-op'd by the aggregate, not filtered out by the adapter.
- [x] **Task 2.3.4**: GREEN — remove the `status() == CONFIRMED` filter at `TourStartedListener:43`; rely on `markActive`'s own guard (`TourBooking:164-169`). Note `markActive` currently *throws* for CANCELLED, so decide with 0.2's ruling whether the aggregate should no-op or the listener should catch.
- [x] **Task 2.3.5**: REFACTOR — the listener should forward and let the domain decide; verify it holds no business logic per § 4.8.
- [x] **Task 2.3.6**: Update `uc06-mark-booking-active.spec.md` § 5 flow and § 8 failure scenarios; tick the listener DoD box.
- [x] **Task 2.3.7**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 2.3.8**: Run `./gradlew clean test` and `./gradlew build`.

### 2.4 - Relocate the `shared.outport` adapters (A4) — blocked on 0.1, 0.4, 1.5.1
- [x] **Task 2.4.1**: Baseline evidence per U4 — suite green, test count recorded.
- [x] **Task 2.4.2**: Move `LoggingDomainEventPublisher` and `SystemClockPort` out of `booking.outbound.integration` to wherever 0.1 ruled.
- [x] **Task 2.4.3**: Add `bootstrap/SharedConfig` (or per the ruling) and remove the `ClockPort`/`DomainEventPublisher` beans from `BookingConfig`.
- [x] **Task 2.4.4**: Verify `guide` no longer depends on `BookingConfig` for a clock, and that `GuideOperationsConfig` declares what it needs.
- [x] **Task 2.4.5**: Update `ports/clock.outport.spec.md` § 4 and `ports/domain-event-publisher.outport.spec.md` § 4 reference-implementation sections.
- [x] **Task 2.4.6**: Confirm the suite is unchanged — same count, all green.
- [x] **Task 2.4.7**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 2.4.8**: Run `./gradlew clean test` and `./gradlew build`.

---

## Phase 3: Close the test gaps

Pure test additions. Each closes a named UC DoD box. No dependency on Phase 0.

### 3.1 - `StartTourDriverTest` (C1)
- [x] **Task 3.1.1**: RED — `start_happyPath_returnsRunningStatus`. Run it, quote the failure.
- [x] **Task 3.1.2**: RED — `start_usesClockPort_whenStartedAtIsNull` and `start_usesProvidedStartedAt_whenNotNull`, mirroring `MarkBookingActiveDriverTest`'s clock cases.
- [x] **Task 3.1.3**: RED — `start_throwsGuideTourNotFoundException_whenNotFound`, `start_throwsInvalidGuideTourStateException_whenAlreadyRunning`, `start_propagatesTourStartTooEarlyException`.
- [x] **Task 3.1.4**: RED — `start_publishesTourStartedEvent` and `start_callsUpdateOnRepository`; `start_doesNotCallUpdate_whenStateInvalid`.
- [x] **Task 3.1.5**: Use stubs over mocks per `test.definition.md` § 2.2; follow `StartTourDriver`'s existing collaborators.
- [x] **Task 3.1.6**: Tick the UC05 driver DoD box; refresh this scoreboard.
- [x] **Task 3.1.7**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 3.1.8**: Run `./gradlew clean test` and `./gradlew build`.

### 3.2 - UC01 web-layer 400s (C3)
- [x] **Task 3.2.1**: RED — `postWithParticipantCountBelowMinimum_returns400` in `TourBookingControllerTest`. Run it, quote the failure.
- [x] **Task 3.2.2**: RED — `postWithPastTourDate_returns400`.
- [x] **Task 3.2.3**: Determine whether either needs a Bean Validation annotation on `RequestTourBookingRequest` or is already enforced — if the test passes immediately, the gap was in coverage, not behaviour. Say which.
- [x] **Task 3.2.4**: Tick UC01's AC-03/AC-04 REST-boundary box; refresh this scoreboard.
- [x] **Task 3.2.5**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 3.2.6**: Run `./gradlew clean test` and `./gradlew build`.

### 3.3 - UC04 web-layer 400 and 502 (C4)
- [x] **Task 3.3.1**: RED — `changeParticipants_returns400_whenCountBelowMinimum` (`@Min(1)` on `ChangeParticipantsRequest` should already enforce it; confirm).
- [x] **Task 3.3.2**: RED — `changeParticipants_returns502_whenAvailabilityUnavailable`, mirroring `postWithAvailabilityFailure_returns502`.
- [x] **Task 3.3.3**: Tick both UC04 web boxes; refresh this scoreboard.
- [x] **Task 3.3.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 3.3.5**: Run `./gradlew clean test` and `./gradlew build`.

### 3.4 - UC04 persistence roundtrip (C6)
- [x] **Task 3.4.1**: RED — `TourBookingJooqRepositoryIT.update_changesParticipantCount_inDatabase`. Run it, quote the failure.
- [x] **Task 3.4.2**: If the mapper already persists it, the gap was coverage — state that rather than inventing a change.
- [x] **Task 3.4.3**: Tick UC04's persistence box; refresh this scoreboard.
- [x] **Task 3.4.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 3.4.5**: Run `./gradlew clean test` and `./gradlew build`.

### 3.5 - UC06 persistence roundtrip (C5)
- [x] **Task 3.5.1**: RED — `TourBookingJooqRepositoryIT.update_changesStatus_toActive_withStartedAtAndGuideTourId`. Run it, quote the failure.
- [x] **Task 3.5.2**: Verify `started_at` and `guide_tour_id` survive the roundtrip; check the V1 migration actually has those columns before assuming a mapper bug.
- [x] **Task 3.5.3**: Tick UC06's persistence box; refresh this scoreboard.
- [x] **Task 3.5.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`.
- [x] **Task 3.5.5**: Run `./gradlew clean test` and `./gradlew build`.

---

## Phase 4: Naming alignment

**Entire phase gated on 0.6 choosing option (a).** Skip if (b) or (c). Behaviour-preserving
— follow the U4 ruling.

### 4.1 - `GuideOperationsConfig` → `GuideConfig` (B1)
- [x] **Task 4.1.1**: Baseline evidence per U4 — suite green, count recorded.
- [x] **Task 4.1.2**: Rename the class and file.
- [x] **Task 4.1.3**: Update every `SDD:` Javadoc citation and any `@Import`/reference.
- [x] **Task 4.1.4**: Confirm the suite is unchanged; dispatch both agents; run the gates.

### 4.2 - `GuideOperationsExceptionHandler` → `GuideExceptionHandler` (B2)
- [x] **Task 4.2.1**: Baseline evidence per U4.
- [x] **Task 4.2.2**: Rename the class and file.
- [x] **Task 4.2.3**: Update `GuideTourControllerTest`'s Javadoc reference and any others.
- [x] **Task 4.2.4**: Confirm the suite is unchanged; dispatch both agents; run the gates.

---

## Phase 5: Make the rules mechanical

Must come after Phases 2–4 or ArchUnit lands red. The highest-value phase in this plan.

### 5.1 - ADR for ArchUnit
- [x] **Task 5.1.1**: A new test dependency is ADR trigger 2 (`sdd.playbook.md` § 6). Write `adr/0007-archunit-boundary-enforcement.adr.md` — why mechanical enforcement in addition to `ddd-hex-reviewer`, what it covers, what it deliberately cannot (anemic-model detection stays a judgement call).
- [x] **Task 5.1.2**: Wait for confirmation before implementing (`execution.playbook.md` § 3.2.4).

### 5.2 - ArchUnit: dependency rules (§ 6)
- [x] **Task 5.2.1**: Add the ArchUnit dependency to `libs.versions.toml` and `app/build.gradle.kts` (test scope).
- [x] **Task 5.2.2**: RED — one test per § 6 rule, seven in all, in `app/src/test/.../architecture/DependencyRulesTest.java`. Run them, quote failures.
- [x] **Task 5.2.3**: GREEN — all seven pass. Any that cannot is a real violation Phase 2 missed; fix the code, not the rule.
- [x] **Task 5.2.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter`; run the gates.

### 5.3 - ArchUnit: context registry (§ 11)
- [x] **Task 5.3.1**: RED — assert top-level packages are exactly `booking`, `bootstrap`, `guide`, `shared`, so a new one fails the build rather than waiting for review.
- [x] **Task 5.3.2**: RED — no `booking` → `guide` and no `guide` → `booking` import; `shared` imports no context. Promotes `tasks.md` Task 10.3's old throwaway grep into a permanent gate.
- [x] **Task 5.3.3**: GREEN; dispatch both agents; run the gates.

### 5.4 - ArchUnit: class roles and domain purity
- [x] **Task 5.4.1**: RED — HTTP annotations only on `*RestAPI`, never on `*Controller` (§ 4.5). This is the violation the planted-drift test proved is silently breaking.
- [x] **Task 5.4.2**: RED — no Spring/Jakarta/Jackson/jOOQ import in any `core` or `shared` package; no `Instant.now()`/`LocalDate.now()`/`new Date()` in the domain (§ 8).
- [x] **Task 5.4.3**: RED — naming conventions: `*Command`, `*Result`, `*UseCase`, `*Driver`, `*Request`, `*Response`, `*RestAPI`, `*Controller`.
- [x] **Task 5.4.4**: RED — identity rule per ADR 0005: no context imports another context's identity type.
- [x] **Task 5.4.5**: GREEN; dispatch both agents; run the gates.

### 5.5 - Spotless (G3)
- [x] **Task 5.5.1**: Apply `alias(libs.plugins.spotless)` — already in the version catalog, never applied — and configure it to match the existing style rather than reformatting the tree.
- [x] **Task 5.5.2**: Run `spotlessCheck`; if it wants a large diff, tune the config instead of accepting churn in the same block.
- [x] **Task 5.5.3**: Add `spotlessCheck` to `test.definition.md` § 7's canonical gate list.
- [x] **Task 5.5.4**: Run `./gradlew clean test` and `./gradlew build`.

### 5.6 - CI (G4)
- [x] **Task 5.6.1**: Add `.github/workflows/build.yml` running `./gradlew clean test build` plus `spotlessCheck` on push and PR.
- [x] **Task 5.6.2**: **Pin the JDK explicitly** (Temurin 25) rather than inheriting a runner default — ADR 0006's Future Considerations calls this out, and it is the same divergence class the vendor pin just fixed.
- [x] **Task 5.6.3**: Assert `app/data/` does not exist after the test run, per `test.definition.md` § 1.3.
- [x] **Task 5.6.4**: Confirm the workflow is green before merging.

### 5.7 - Record the jacoco decision (G5)
- [x] **Task 5.7.1**: Confirm with the user that coverage stays qualitative, then say so explicitly in `test.definition.md` § 6 so the absence reads as a decision, not an omission.

### 5.8 - Full-tree clean bill
- [x] **Task 5.8.1**: Dispatch `ddd-hex-reviewer` over the whole tree. Target: `PASS` with an empty `Pre-existing` list.
- [x] **Task 5.8.2**: Dispatch `spec-documenter` over all contexts. Target: no `Conflicts`, no `Gaps`.
- [x] **Task 5.8.3**: Tick the `ddd-hex-reviewer: PASS` and `Quality gates green` boxes across UC01–UC06 — 12 of the 21 open items close here, and only here.
- [x] **Task 5.8.4**: Run `./gradlew clean test` and `./gradlew build` from a clean clone.
- [x] **Task 5.8.5**: Refresh both scoreboards; the plan DoD should be fully ticked.

---

## Phase 6: Feature work

Only now genuinely `/loop-uc`-drivable: the DoDs are honest and the reviewer is clean.
Each use case is one `/loop-uc UC<nn>` run, not a hand-written task block.

### 6.1 - UC11 CompleteTour
- [x] **Task 6.1.1**: UC11 implemented — `GuideTour.complete(Instant)`, `CompleteTourDriver`, `V3__DDL_add_guide_tour_completed_at.sql`, `rest/uc11-complete-tour.http`. Commit `b075c73`.
- [x] **Task 6.1.2**: Ran as the documented loop rather than via `/loop-uc`. `ddd-hex-reviewer` returned six findings; finding 6 was a real latent bug — invariant I-06 was relied on by `complete` but unenforced anywhere — fixed with its own RED test.

### 6.2 - UC07 MarkBookingCompleted
- [x] **Task 6.2.1**: UC07 implemented — `TourBooking.markCompleted(Instant, String)`, `BookingCompleted`, the `MarkBookingCompleted*` inport triple, `MarkBookingCompletedDriver`, `TourCompletedListener`, and `TourBookingRepository.findActiveByTourId` with its jOOQ IT.
- [x] **Task 6.2.2**: Behaviour and contracts complete — 32 UC07 test methods on disk:
  `TourBookingTest.markCompleted_*` (8), `MarkBookingCompletedDriverTest` (12),
  `TourCompletedListenerTest` (9), `TourBookingJooqRepositoryIT` (3). An earlier
  revision claimed 21. Three more landed with task 6.2.3, for 35.
- [x] **Task 6.2.3**: Both `spec-documenter` contradictions resolved, in opposite directions.
  (a) § 3/§ 8 claimed not-found was "no-op, logged" — no catch or logger exists in either
  listener, so the **spec** was wrong; corrected, along with the same false claim in UC06 § 3,
  where it had been wrong since UC06 shipped. (b) § 2 listed `guideTourId` as an input that
  nothing propagated, while UC06 threads the same id into `BookingActivated` — the **code**
  was wrong. Propagated rather than de-scoped: half a correlation trail is worse than none,
  because it looks complete. Widened `BookingCompleted`, `MarkBookingCompletedCommand` and
  `markCompleted`, driven by a compile-failure RED. Each of the three hops has its own test
  and was mutation-verified (aggregate → 3 failures, driver → 1, listener → 1).
- [x] **Task 6.2.4**: `ddd-hex-reviewer` `DRIFT` on pass 1 — `MarkBookingCompletedUseCase`
  carried `@throws` on the type rather than the method, reintroducing the very defect UC11
  fixed in `CompleteTourUseCase` earlier in this same increment. Fixed; `PASS` on pass 2.
- [x] **Task 6.2.5**: `./gradlew clean test build` green, 262 tests, 0 failures.

### 6.3 - UC08 CancelBookingByUser
- [x] **Task 6.3.1**: H1 resolved — **no ADR**. The change is confined to one aggregate's method signature and its callers, which is not an architectural decision under `sdd.playbook.md` § 6. (H1's `String` third parameter became `CancellationReason` during implementation — UC08 § 10 Contracts records the deviation and why.)
- [ ] **Task 6.3.2**: `/loop-uc UC08`. Note UC08 **modifies UC03's endpoint**, so UC03's spec and `rest/uc03-cancel-tour-booking.http` change in the same increment. *In progress: implementation, tests, migration, REST file and all spec reconciliation are on disk (24/26 DoD boxes); the loop exits only on a witnessed `ddd-hex-reviewer: PASS` and green quality gates.* The endpoint verb changed `DELETE` → `POST .../cancel` — a maintainer-ruled breaking change, recorded in UC08 § 9 and UC03 § 9.
- [x] **Task 6.3.3**: Confirm UC03's DoD is still fully ticked afterwards — re-verified against disk during UC08's reconciliation: all 11 boxes remain ticked, every named test method exists (`cancel_*`, `cancelBooking_*`, `update_changesStatus_toCancelled_afterCancel`), and the `.http` file covers 200/400/400/404/409 on the new route.

### 6.4 - UC12 + UC09 cancellation by guide
- [x] **Task 6.4.1**: H2 resolved — ADR-0008 was written and then **Rejected** by the owner. The driver orchestrates and calls the booking inport synchronously; a `BookingCancellationPort` would only relocate the coupling behind an outport whose single implementation delegates to that very inport. `architecture.definition.md` § 11 rule 3 amended to permit driver-to-inport calls, and `ContextRegistryTest` enforces that only drivers may make them.
- [ ] **Task 6.4.2**: `/loop-uc UC12` (guide side; the driver orchestrates, no new outport).
- [ ] **Task 6.4.3**: `/loop-uc UC09` (booking side implementation).
- [ ] **Task 6.4.4**: Verify the transaction-rollback acceptance criterion (UC12 AC-07) has a real integration test, not just a unit test.

---

## Sequencing

```
0.1–0.6 (rulings) ─┬─> 1.4, 1.5 ──┬─> 2.1, 2.2, 2.4 ─┐
                   │              │                   │
                   └─> 4.1, 4.2 ──┘   2.3 ────────────┼─> 5.1–5.8 ─> 6.1–6.4
                                                      │
1.1, 1.2, 1.3, 1.6, 1.7 ──────────────────────────────┤
3.1–3.5 ──────────────────────────────────────────────┘
```

Startable now with no ruling: **1.1, 1.2, 1.3, 1.6, 1.7, 3.1, 3.2, 3.3, 3.4, 3.5.**
Everything in Phase 2 and 4 waits on Phase 0. Phase 5 must be last of the cleanup.

## Notes

- 12 of the 21 open UC DoD items are gate-shaped (`ddd-hex-reviewer: PASS`, quality
  gates) and close only at 5.8. The other 9 are real work in Phases 1–3.
- Blocks 2.1, 2.2, 2.4, 4.1 and 4.2 are behaviour-preserving and cannot produce a
  RED. They are all blocked on ruling 0.4 for that reason — starting them before it
  means either faking a RED or silently ignoring `tdd.definition.md` § 2.
- Where a RED unexpectedly goes green (likely in 3.2, 3.3, 3.4), that is a finding:
  the gap was coverage, not behaviour. Report it as such per `tdd.definition.md` § 2.
