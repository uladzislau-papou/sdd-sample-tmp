# Alpine Booking — remaining work

**Read this first if you are picking the project up after time away.**

## What this repository is

A showcase for **Spec-Driven Development and agentic workflow**. The Tour Booking domain
is a vehicle, not the product. When judging what to do next, "does this demonstrate the
method better?" outranks "does this make the booking system more complete."

## Where it stands

**Kotlin migration**: `documentation/adr/0009-kotlin-migration.adr.md`. The Java
implementation (twelve use cases, 32 commits of modernization work — see git history on
this branch prior to the migration commit) was deleted rather than ported. The SDD
framework itself — every file under `documentation/`, `.claude/`, `rest/` — survived
unchanged; only `app/src` and the language-specific portions of the governing docs
(`technical.spec.md`, `coding-style.definition.md`) changed.

Current state: Kotlin 2.4.10 / JVM 25 build compiles, `AlpineBookingApplication` boots,
zero use cases implemented. The Flyway migrations (`app/src/main/resources/db/migration`)
were kept — they describe schema state twelve prior use cases already validated, and
there was no reason to redo that thinking.

**Nothing in `documentation/use-cases/`, `documentation/domain/` or `documentation/ports/`
changed.** Re-implementation restarts from those specs, unchanged, through the normal
`/uc-to-plan` → `/plan-to-task` → `/execute-task` (or `/loop-uc`) flow — one use case at a
time, starting with UC01.

## Why a mechanical port was rejected

Recorded in full in ADR 0009. In short: re-implementing through the spec-driven loop is
itself a demonstration of the method this project exists to showcase
(`project.definition.md`); a file-by-file Java→Kotlin transliteration would not be, and
would also encode Kotlin as "Java with different syntax" rather than use what the language
actually offers (data classes, sealed hierarchies, null-safety).

---

## Phase 1 — Re-implement the use cases

Twelve specs exist and are unchanged. Each is re-implemented independently through the
inner loop (`execution.playbook.md`) or the outer loop (`loop.playbook.md`,
`/loop-uc <UCNN>`), in the order the domain naturally depends on:

- [ ] UC01 RequestTourBooking
- [ ] UC02 ConfirmTourBooking
- [ ] UC03 CancelTourBooking
- [ ] UC04 ChangeParticipants
- [ ] UC05 StartTour
- [ ] UC06 MarkBookingActive
- [ ] UC07 MarkBookingCompleted
- [ ] UC08 CancelBookingByUser
- [ ] UC09 CancelBookingByGuide (`documentation/use-cases/uc09-cancel-booking-by-guide.spec.md`)
- [ ] UC11 CompleteTour
- [ ] UC12 CancelTourByGuide

UC10 is `SUPERSEDED` (split into UC05/UC11/UC12 in the Java-era work; the spec file
records this — confirm it still reads correctly before starting UC05).

Each use case's own `## 10. Definition of Done` is the authoritative exit condition per
`sdd.playbook.md` § 4.2 — this list is a sequencing note, not a substitute DoD.

## Phase 2 — Re-establish enforcement

The Java implementation had 30 ArchUnit rules enforcing `architecture.definition.md` § 6
and § 11. None exist yet in Kotlin. Do not defer this indefinitely — a review agent
(`ddd-hex-reviewer`) catches drift on inspection, but an ArchUnit suite catches it in CI on
every commit, which is a materially different guarantee. Reintroduce rules incrementally,
alongside the use case that first makes them meaningful (e.g. context-boundary rules once
two contexts exist), rather than as one large upfront task.

## Phase 3 — Republish the showcase / other backlog

The pre-migration `plan.md` carried a long backlog (public-repo publishing, doctrine debt,
test-depth gaps, domain gaps) accumulated over the Java implementation's lifetime. That
history — including the specific defects the review agents found and the working notes for
a cold start — is preserved in git history on this branch, in the commit immediately before
the Kotlin migration. Re-triage it once the domain exists again in Kotlin; most of it
(doctrine debt, test-depth gaps found in Java code) does not carry forward directly since
the code it refers to no longer exists, but the *shape* of what was found is worth rereading
before repeating the same defects.

---

## Definition of Done for this plan

- [ ] All twelve use cases re-implemented in Kotlin, each closing its own spec's DoD.
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree with `Undocumented: none`.
- [ ] `spec-documenter` reports no `Gaps` and no unresolved `Conflicts`.
- [ ] `./gradlew clean test build` green.
- [ ] ArchUnit enforcement re-established for every rule the Java-era suite carried that
      still applies.
