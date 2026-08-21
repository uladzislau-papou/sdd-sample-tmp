# Plan – Clean Baseline (no open ends)

Replaces the previous `plan.md`, which was the fully-executed ADR-0003 guide-context
extraction (all 51 tasks in `tasks.md` ticked).

## Goal

Close every finding surfaced by the `ddd-hex-reviewer` and `spec-documenter`
verification runs, so that:

- `ddd-hex-reviewer` returns `PASS` on the whole tree, not just on an increment
- every DoD box in UC01–UC06 is ticked with named evidence
- no documented rule is dead text, and no rule needed by the reviewer is unwritten
- the § 6 dependency rules are mechanically enforced, not review-enforced

## Findings inventory

31 open items. Legend: **[D]** needs your decision · **[doc]** documentation only ·
**[code]** production or test code · **[tool]** build/CI.

### Architecture violations
| # | Finding | Rule |
|---|---------|------|
| ~~A1~~ | `BookingExceptionHandler:7` imports `booking.core.outport.AvailabilityUnavailableException` | `architecture.definition.md` § 6.3 / § 4.5 — controllers depend only on `core.inport` |
| ~~A2~~ | `RequestTourBookingUseCase:7` imports a `core.outport` type | § 4.2 — usecase interfaces may reference only `inport.command`, `inport.result`, `core.domain.<agg>.exception` |
| ~~A3~~ | `TourStartedListener:43` filters `status() == CONFIRMED` | § 4.8 — listeners must not contain business logic. Also untested |
| ~~A4~~ | `LoggingDomainEventPublisher`, `SystemClockPort` implement `shared.outport` from inside `booking`; `guide` consumes them via `BookingConfig` | § 4.7 + ADR-0003's claim "both contexts depend on `shared.domain` only", now false. **Blocked on U1** |

A2 is A1's root cause: the exception is part of the inport contract but lives in
`core.outport`. Fix A2 first and A1 dissolves.

### Naming divergence from ADR-0003
| # | Finding |
|---|---------|
| ~~B1~~ | `GuideOperationsConfig` — ADR-0003 § Decision and `architecture.definition.md` § 3 (`<ContextName>Config`) both say `GuideConfig` |
| ~~B2~~ | `GuideOperationsExceptionHandler` — ADR-0003 says `GuideExceptionHandler` |

### Missing tests
| # | Finding | Rule |
|---|---------|------|
| ~~C1~~ | No `StartTourDriverTest` — every booking driver has one | `test.definition.md` § 2.2 (mandatory), § 6 |
| ~~C2~~ | No test for `TourStartedListener` / `TourBookingEventListener` — UC06's fan-out and the ADR-0002 `AFTER_COMMIT` boundary are unverified | § 2.2, § 6 |
| ~~C3~~ | UC01: no web test asserting 400 for `participantCount < 1` or a past `tourDate` | § 2.4 |
| ~~C4~~ | UC04: no web test for 400 or 502 | § 2.4 |
| ~~C5~~ | UC06: persistence IT does not cover the ACTIVE transition, `started_at`, or `guide_tour_id` | § 2.3, § 6 |
| ~~C6~~ | UC04: persistence IT does not cover a `participant_count` update | § 2.3, § 6 |

### Missing specs
| # | Finding |
|---|---------|
| ~~D1~~ | `documentation/domain/aggregate-guide-tour.spec.md` — the `guide` context has no domain spec; `GuideTour`'s invariants and state model are undocumented |
| ~~D2~~ | `documentation/ports/guide-tour-repository.outport.spec.md` — outport implemented, unspecified |
| ~~D3~~ | `documentation/ports/start-tour.inport.spec.md` — `booking`'s counterpart exists |

### Stale or dead documentation
| # | Finding |
|---|---------|
| ~~E1~~ | `coding-style.definition.md` § 3.2 names the layers `domain / application / in / out / adapter.*` with direction `adapter → application → domain`. The enforced ontology is `core / inbound / outbound`. No code follows it — dead text that misleads anyone treating it as authoritative |
| ~~E2~~ | `tasks.md` is the completed ADR-0003 checklist with four stale paths (`guide/core/domain/*`, `guide/core/domain/event/TourStarted`, `GuideExceptionHandler`, `GuideConfig`) |
| ~~E3~~ | `plan.md` was the completed ADR-0003 plan — **resolved by this file** |
| ~~E4~~ | ADR-0003 names `GuideConfig`/`GuideExceptionHandler` and sketches `guide.core.domain` + a guide-local event package; disk differs on all three. ADRs are immutable |
| ~~E5~~ | `notes.md` records three gaps; two independently confirmed (guide domain spec missing, no read side) |

### Undocumented rules — needed before dependent fixes
| # | Question |
|---|----------|
| ~~U1~~ | **Where do adapters for `shared.outport` live?** § 3's tree gives `shared` only `domain/event`; nothing says whether a `ClockPort` implementation belongs in `shared.outbound`, in `bootstrap`, or in one arbitrary context (today: `booking`). **A4 is unresolvable until this is written down** |
| ~~U2~~ | **Is there a one-aggregate-per-transaction rule?** `TourStartedListener` updates every CONFIRMED booking for a tour in one `REQUIRES_NEW` transaction. § 10 forbids only cross-aggregate *invariants*, so this is not currently a violation — but if per-aggregate transactions are intended, it needs writing down |
| ~~U3~~ | **How is spec-before-code ordering made auditable for *rule* changes?** `tdd.definition.md` § 2 makes TDD auditable for code — the quoted RED failure is the evidence. Nothing does the equivalent for doctrine. A rule and the code it sanctions, landing in one snapshot, are indistinguishable from the code arriving first and the rule being written to authorise it. Found by `ddd-hex-reviewer` against this very change: `test.definition.md` § 1.3 names `WebTestApplication`/`GuideWebTestApplication`, and arrived in the same uncommitted tree as those classes. It declined to call it drift (the rule *tightens* and cites five-month-old precedent rather than inventing cover) but reported the unverifiability |

### Build and tooling
| # | Finding |
|---|---------|
| ~~G1~~ | **RESOLVED.** Baseline bumped to Java 25 LTS with the vendor pinned to Temurin — `adr/0006-java-25-baseline.adr.md`. Toolchain, `.sdkmanrc` and IDE all on `25.0.4-tem`; verified class file major version 69 and 124/124 tests green |
| ~~G2~~ | **No ArchUnit.** § 6's seven "enforceable" rules are enforced only by model-driven review. A1/A2/A4 existed for months precisely because nothing checked |
| ~~G3~~ | Spotless is in `libs.versions.toml` `[versions]` and `[plugins]` but never applied — no formatting gate |
| ~~G4~~ | No CI. Quality gates run only when a human or agent remembers |
| ~~G5~~ | No jacoco. `test.definition.md` § 6 targets qualitative coverage, so this may be deliberate — confirm |
| ~~G6~~ | `.gitignore` covers `/data/` but not `app/data/`. Moot now that no test writes there, but latent |
| ~~G7~~ | `settings.local.json` has a stray `Bash(test:*)` matching the shell builtin, not Gradle |

### Governance decisions blocking feature work
| # | Item |
|---|------|
| H1 | **Resolved — no ADR.** UC08 replaces `TourBooking.cancel(Instant)` with `cancel(Instant, CancelledBy, String)`. Ruled a within-aggregate signature change, not an architectural one |
| H2 | **Resolved — ADR-0008 written, then Rejected.** The use case, implemented in the inbound driver, orchestrates: cancel the guide tour, then call the booking inport synchronously inside the same transaction. No new outport, so no ADR. `architecture.definition.md` § 11 rule 3 amended to permit driver-to-inport calls |
| H3 | **Closed.** UC11 is implemented; `CompleteTourDriver` publishes `TourCompleted` post-commit |

---

## Phase 0 — Decisions (no code)

Nothing here is mine to decide. Five rulings unblock the rest.

- [x] **U1** — where `shared.outport` adapters live. Recommendation: `shared.outbound.*`,
      wired in a new `bootstrap/SharedConfig`. It keeps the shared kernel's ports and
      their adapters symmetric, and stops `guide` depending on `BookingConfig` for a
      clock. Write it into `architecture.definition.md` § 9, then A4 becomes mechanical.
- [x] **U2** — one-aggregate-per-transaction. Recommendation: document it as a
      *guideline*, not a rule, and leave `TourStartedListener` as-is. Making it a rule
      forces per-booking transactions and an outbox for the fan-out, which is a bigger
      design change than the finding warrants.
- [x] **E4 / B1 / B2** — the ADR-0003 naming divergence. Three options: rename the
      classes to match the ADR (recommended — the ADR and `architecture.definition.md`
      § 3 agree, and `GuideOperations*` is a leftover from when the context was called
      `guideoperations`); or write a superseding ADR blessing the current names; or
      record it as accepted historical drift.
- [x] **E1** — `coding-style.definition.md` § 3.2. Recommendation: delete the section
      and replace it with a pointer to `architecture.definition.md` § 3. It is the only
      place in the docs describing a layering that does not exist.
- [x] **G1** — **Done.** Java 25 LTS baseline, Temurin pinned, ADR 0006 written.
- [x] **U3** — auditable ordering for doctrine changes. Recommendation: a commit-discipline
      rule in `sdd.playbook.md`, not more prose in the definitions —
      **a change to a `*.definition.md` or `*.playbook.md` lands in its own commit, before
      any code that relies on it.** Then ordering is a fact in the history rather than a
      claim in a report, and `ddd-hex-reviewer` can check it with
      `git log --diff-filter=M -- documentation/` instead of reporting "unverifiable".
      Cheap, mechanical, and it closes the one hole the reviewer cannot otherwise see
      through. Worth adopting before Phase 2, since Phase 2 changes rules and code together.

## Phase 1 — Documentation only

No code, no build risk. Can run in parallel with Phase 0's decisions.

- [x] **D1** `aggregate-guide-tour.spec.md` from `domain.spec.template.md` — reverse-engineer
      `GuideTour`'s invariants, the SCHEDULED → RUNNING → FINISHED/CANCELLED state model,
      and `TourStarted` emission. Feeds UC11 and UC12's DoD.
- [x] **D2** `guide-tour-repository.outport.spec.md`, **D3** `start-tour.inport.spec.md`,
      following the existing five port specs' shape.
- [x] **E1** apply the § 3.2 ruling.
- [x] **E2** rebuild `tasks.md` as a live DoD scoreboard (`/loop-uc` rebuilds it per
      iteration, so seeding it with UC01–UC06's current state is enough).
- [x] **E5** prune `notes.md` entries now tracked as DoD items; keep the read-model musing.
- [x] **G6** add `app/data/` to `.gitignore`; **G7** drop `Bash(test:*)`.

## Phase 2 — Architecture fixes (TDD, `/loop-uc`-drivable)

Order matters: A2 before A1.

- [x] **A2** move `AvailabilityUnavailableException` to the inport contract surface —
      `booking.core.domain.tourbooking.exception` is the documented home (§ 4.2 permits
      usecase interfaces to reference it there). Touches the usecase, the driver, the
      handler and `StubAvailabilityChecker`.
- [x] **A1** falls out of A2 — verify `BookingExceptionHandler` imports only `core.inport`
      and domain exceptions afterwards.
- [x] **A3 + C2** together. Write the listener test first (C2), which will pin the current
      fan-out behaviour, then move the CONFIRMED decision onto the aggregate — `markActive`
      already owns "which statuses may activate" (`TourBooking:164-169`), so the listener
      should attempt and let the aggregate no-op rather than pre-filtering.
- [x] **A4** after U1 — relocate the two adapters, add `SharedConfig`, remove `guide`'s
      dependency on `BookingConfig`.

Each item is one RED → GREEN → REFACTOR cycle with a drift review, per
`execution.playbook.md` § 3.4–3.5.

## Phase 3 — Close the test gaps

- [x] **C1** `StartTourDriverTest` — happy path (both clock-supplied and explicit
      `startedAt`), not-found, invalid state, too-early. Closes the one open behaviour box
      in UC05's DoD.
- [x] **C3** UC01 web tests for 400 on `participantCount < 1` and past `tourDate`.
- [x] **C4** UC04 web tests for 400 and 502.
- [x] **C5** UC06 persistence IT for the ACTIVE transition incl. `started_at` / `guide_tour_id`.
- [x] **C6** UC04 persistence IT for a `participant_count` update.

After Phase 3, UC01–UC06's DoDs are fully ticked except the gate-shaped items.

## Phase 4 — Naming (only if Phase 0 chose "rename")

- [x] **B1** `GuideOperationsConfig` → `GuideConfig`; **B2**
      `GuideOperationsExceptionHandler` → `GuideExceptionHandler`. Mechanical rename plus
      every `SDD:` Javadoc citation and `GuideTourControllerTest`'s reference.

## Phase 5 — Make the rules mechanical

This is what stops the baseline decaying again. Do it *after* Phases 2–4 so it starts green.

- [x] **G2 ArchUnit** — the highest-value item in this plan. Encode
      `architecture.definition.md` § 6's seven rules, § 11's context registry (including
      no `booking` ↔ `guide` imports), the `*RestAPI`/`*Controller` annotation split, and
      the no-`Instant.now()`-in-domain rule. `architecture.definition.md` § 1 has
      anticipated this since March. New test dependency → ADR trigger 2.
      Turns `ddd-hex-reviewer` from the only guard into a second opinion.
- [x] **G3** apply Spotless (already in the catalog, just unapplied) and add it to the gates.
- [x] **G4** CI running `./gradlew clean test build` plus the new ArchUnit and Spotless
      checks on push.
- [x] **G5** confirm jacoco stays out, and say so in `test.definition.md` § 6 so its
      absence reads as a decision rather than an omission.

## Phase 6 — Feature work

Now genuinely `/loop-uc`-drivable, since the DoDs are honest and the reviewer is clean.

- [x] **UC11** CompleteTour — unblocks UC07 by publishing `TourCompleted` (H3)
- [x] **UC07** MarkBookingCompleted
- [ ] **UC08** CancelBookingByUser — H1 resolved (no ADR); modifies UC03's endpoint and spec
- [ ] **UC12** + **UC09** together — H2 resolved; driver-orchestrated, no ADR

## Carried forward — DONE

Three findings that surfaced during the cleanup, all the same defect. **Completed** -
see the increment below.

**Unmapped `IllegalArgumentException` surfaces as 500 where 400 is correct.**

| Site | Trigger | Before | Now |
|------|---------|--------|-----|
| `UUID.fromString` in all five drivers | malformed id in a path variable | 500, unmapped, untested | **400**, mapped in both handlers, tested at both layers |
| `ParticipantContact` constructor | blank name/email in a command | 500 | **400** via `InvalidBookingRequestException` |
| `TourId` constructor | blank `tourId` in a command | 500 | **400** via the `IllegalArgumentException` mapping — exempt, see below |

**`TourId` could not be fixed the same way, and the architecture tests proved it.**
`shared.domain` may not depend on a bounded context (§ 9), so `TourId` has no domain
exception available. Attempting it fails
`ContextRegistryTest.shared_dependsOnNoBoundedContext` — verified by trying it. The
alternative, a `shared.domain.exception` package, would grow the shared kernel to serve one
blank-string check and force every context to map a second exception type. So the boundary
mapping is the resolution, and `coding-style.definition.md` § 6.2 now carries an explicit
shared-kernel exemption that is *not* a general licence.

All three have the same shape: a value built from a command throws
`IllegalArgumentException`, no `@ExceptionHandler` maps it, and no test covers it. Bean
Validation hides all three today — which is exactly why `coding-style.definition.md` § 6.2
now says boundary validation is not part of the core's contract. Drive these use cases from
anything other than the REST adapter and they surface as 500s.

Driven by a RED per site. One taught me something: the first web test asserted a malformed
path variable directly, and failed with a `NullPointerException` rather than a 400 — because
`UUID.fromString` runs in the *driver*, and a slice test mocks the inport, so the malformed
id never reaches it. The test was at the wrong layer. Split into the mapping (web slice,
mock throws) and the source (driver test).

Recorded in `ports/start-tour.inport.spec.md` § 7 and `coding-style.definition.md` § 6.2.

## Sequencing

```
Phase 0 (decisions) ─┬─> Phase 1 (docs) ────────┐
                     ├─> Phase 2 (arch fixes) ──┼─> Phase 5 (ArchUnit/CI) ─> Phase 6
                     └─> Phase 4 (renames) ─────┤
                         Phase 3 (test gaps) ───┘
```

Phases 1 and 3 have no dependency on Phase 0 except where noted (E1, U1) and can start
immediately. Phase 5 must come last of the cleanup phases or ArchUnit lands red.

## Definition of Done for this plan

- [x] `ddd-hex-reviewer` returns `PASS` on the full tree with an empty `Pre-existing` list
- [x] `spec-documenter` reports no `Conflicts` and no `Gaps`
- [x] Every UC01–UC06 DoD box ticked with named evidence
- [x] `Undocumented` is empty — no rule the reviewer needs is unwritten
- [x] ArchUnit enforces `architecture.definition.md` § 6 and § 11 in CI
- [x] `./gradlew clean test build` green from a clean clone, and `app/data/` never appears
