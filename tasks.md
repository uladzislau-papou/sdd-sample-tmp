# Tasks – Kotlin Baseline

Derived from `plan.md`. Replaces the previous `tasks.md`, which tracked the completed
Java implementation (80/80 DoD boxes across UC01–UC12, five months of modernization work).
That history is preserved in git, on the commit immediately before the Kotlin migration
(`documentation/adr/0009-kotlin-migration.adr.md`).

Governance: `execution.playbook.md` § 3 for each increment, `tdd.definition.md` for
RED → GREEN → REFACTOR, `loop.playbook.md` for the outer loop.

---

## DoD Scoreboard – Plan

Mirrored from `plan.md` § "Definition of Done for this plan". The plan is
authoritative; this is a scoreboard.

- [ ] All twelve use cases re-implemented in Kotlin, each closing its own spec's DoD
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree with `Undocumented: none`
- [ ] `spec-documenter` reports no `Gaps` and no unresolved `Conflicts`
- [ ] `./gradlew clean test build` green
- [ ] ArchUnit enforcement re-established for every rule the Java-era suite carried that
      still applies

## DoD Scoreboard – Use cases

Mirrored from each use case spec's `## 10. Definition of Done`. Specs are authoritative
and unchanged by the migration — only the implementation was deleted.

| Use case | Status |
|----------|--------|
| UC01 RequestTourBooking | Not started |
| UC02 ConfirmTourBooking | Not started |
| UC03 CancelTourBooking | Not started |
| UC04 ChangeParticipants | Not started |
| UC05 StartTour | Not started |
| UC06 MarkBookingActive | Not started |
| UC07 MarkBookingCompleted | Not started |
| UC08 CancelBookingByUser | Not started |
| UC09 CancelBookingByGuide | Not started |
| UC10 GuideActions | `SUPERSEDED` — split into UC05/UC11/UC12; confirm spec still reads correctly before starting UC05 |
| UC11 CompleteTour | Not started |
| UC12 CancelTourByGuide | Not started |

---

## Phase 1: Re-implement the use cases

One block per use case. Run either the inner loop by hand
(`execution.playbook.md` § 3) or `/loop-uc <UCNN>` end to end.

### 1.1 - UC01 RequestTourBooking
- [ ] **Task 1.1.1**: `/uc-to-plan UC01` → review/fill `plan.md` gaps for this increment.
- [ ] **Task 1.1.2**: `/plan-to-task` → phased `tasks.md` blocks for UC01.
- [ ] **Task 1.1.3**: Execute via `/execute-task` per block, TDD-first (RED quoted before
      GREEN, per `tdd.definition.md`).
- [ ] **Task 1.1.4**: Dispatch `ddd-hex-reviewer` and `spec-documenter` after each GREEN.
- [ ] **Task 1.1.5**: `./gradlew clean test` and `./gradlew build` green; tick UC01 § 10.

### 1.2 - UC02 through UC12
- [ ] Repeat the 1.1 pattern for each remaining use case in `plan.md` Phase 1's order.
      Not expanded block-by-block here to avoid the list going stale before UC01 is even
      done — expand a use case's own five-task pattern when it is actually started.

---

## Phase 2: Re-establish enforcement

- [ ] **Task 2.1**: After the first two bounded contexts exist again (`booking`, then
      `guide`), reintroduce `ContextRegistryTest` (architecture.definition.md § 11) in
      Kotlin.
- [ ] **Task 2.2**: Reintroduce `DependencyRulesTest` (§ 6) once `core`/`inbound`/`outbound`
      packages exist to check.
- [ ] **Task 2.3**: Reintroduce `ClassRoleRulesTest` (naming/role conventions) once enough
      class roles (`*Driver`, `*RestAPI`, `*Repository`, …) exist to be meaningful.
- [ ] **Task 2.4**: Confirm ArchUnit 1.4.1 imports classes correctly at JVM 25 bytecode
      (major version 69) — do not assume; assert the import count directly, per
      `technical.spec.md`'s standing lesson from the Java 25 bytecode incident.

---

## Sequencing

Phase 1 is the whole of the near-term work; it is inherently sequential in that later use
cases (UC06, UC07, UC09, UC12) depend on earlier ones' aggregates and cross-context wiring
existing. Phase 2 interleaves with Phase 1 rather than following it — see each task's
trigger condition above.
