# Alpine Booking — remaining work

**Read this first if you are picking the project up after time away.**

## What this repository is

A showcase for **Spec-Driven Development and agentic workflow**. The Tour Booking domain
is a vehicle, not the product. When judging what to do next, "does this demonstrate the
method better?" outranks "does this make the booking system more complete."

That distinction decides the priorities below. The domain has real gaps — no optimistic
locking, no read side — and they are the *lowest* priority here, because a workshop
attendee learns nothing from them that the existing eleven use cases do not already teach.
The harness gaps are the ones that undermine the point.

## Where it stands

Last talk: **March 2026**. Modernization pass: **August 2026**, 32 commits on
`sdd-modernization`. Eleven live use cases implemented (UC10 is `SUPERSEDED`, split into
UC05/UC11/UC12). Java 25 LTS on Temurin, Spring Boot 4, jOOQ 3.20, ArchUnit 1.4.1.

What the August pass added, beyond use cases:

- `tdd.definition.md` — RED→GREEN→REFACTOR with the **RED evidence rule**: production code
  requires a quoted failure first.
- `loop.playbook.md` + `.claude/skills/loop-uc/` — the outer loop, with a no-progress
  detector and three hard stops.
- Two subagents, dispatched in parallel after every GREEN step: `ddd-hex-reviewer`
  (read-only, returns `PASS`/`DRIFT`) and `spec-documenter` (docs only, no `Bash`, so it
  cannot claim a gate passed).
- 30 ArchUnit rules enforcing `architecture.definition.md` § 6 and § 11, in CI.
- A checked-in `.claude/settings.json`, so the harness is reproducible on clone.

### The thing worth showing a workshop

Five months of green tests hid real defects. The agents found them, and the reason they
were invisible is the interesting part:

| Defect | Why the suite missed it |
|---|---|
| `TourBookingJooqRepository.update` wrote only `status` | UC04's entire effect was discarded; no test asserted a round trip |
| Controller tests wrote a real H2 file into `app/data/` | no `@ActiveProfiles("test")`; state leaked between runs and survived `clean` |
| `guide` took its clock from `BookingConfig` | invisible to any import check, and it made ADR-0003's central claim false |
| Eight documented rules no code ever followed | prose nobody executed |
| `guideTourId` dropped on the UC07 path | half a correlation trail, which looks complete |
| UC09's inport was per-booking | undrivable by UC12 without leaking `booking`'s state model across the boundary |

The last two were found *by the review agents*, mid-increment, in code that had just been
written and was fully green. That is the demo.

---

## Phase 0 — Publish to the public showcase

The public repository is
[`dominikgaller/alpine-booking-reference`](https://github.com/dominikgaller/alpine-booking-reference).
`.github/workflows/publish-showcase.yml` mirrors this one to it on every push to `main`,
excluding the private working material. The excluded paths stay in this repository.

**Excluded:** `.idea/`, `data/`, `app/data/`, `plan.md`, `tasks.md`, and the publish
workflow itself.

- [ ] **Create the public repository** if it does not exist yet. The workflow does not
      create it.
- [ ] **Create a fine-grained PAT** with *Contents: read and write* scoped to
      `alpine-booking-reference` **only**, and add it here as the Actions secret
      `SHOWCASE_PUBLISH_TOKEN`. Not a classic token — a classic `repo`-scoped token would
      grant write access to every repository on the account, to publish one.
- [ ] **Run it once with `dry_run: true`** via *workflow_dispatch* before letting it push.
      The dry run filters, verifies and builds, then reports what it would have published.
- [ ] Decide whether the public repo needs a **README**. There is none today, so the
      showcase would land on an empty front page. This is the single highest-value thing to
      write before pointing anyone at the public repo.

### Why the workflow rewrites history

Removing a file from the tip does not remove it from the history. Running the workflow's
own guard against the unfiltered repository finds **14 paths** that a plain mirror push
would have published:

- all 11 tracked `.idea/` files, including `dataSources.xml`, which typically carries
  database connection configuration
- `app/data/alpine-booking.mv.db` — the H2 file the pre-`test`-profile controller tests
  leaked. It is gone from the working tree and still in history
- `plan.md` and `tasks.md`, which appear in about 20 commits each

So the workflow filters the excluded paths out of the **whole** history with
`git-filter-repo` and force-pushes the result. Deterministic, so the public history is
stable across runs rather than churning. Commit history is preserved on purpose: for this
repository the messages carry the reasoning, the rejected alternatives and the recorded
mistakes, and squashing to one "sync" commit would discard most of what is being shown.

Two safeguards, because an unattended publish that can leak is worse than no publish:

1. The guard inspects every blob in every commit of the filtered result, not just the tip,
   and fails the run rather than pushing. Verified non-vacuous — it fires on the
   unfiltered repository, which is how the 14 paths above were found.
2. The filtered tree is built with `clean test build` before the push. A showcase with a
   red build is worse than none.

### Two problems `ddd-hex-reviewer` raised about the workflow

- [ ] **A force-push to a public repo is not recoverable from the public side.** Every run
      rewrites history and overwrites `main`. The guard and the build gate both run first, so
      a *detected* bad filter never pushes — but a filter that is wrong in a way the guard
      does not model would land irreversibly. Cheapest mitigation: have the workflow push a
      timestamped tag alongside `main`, so the previous good state is recoverable from the
      public repo itself.
- [ ] **The published copy will carry dangling references.** `CLAUDE.md` and
      `loop.playbook.md` both reference `plan.md` and `tasks.md` as artifacts of the
      workflow, and neither file will exist in the public repo. Three options: strip those
      references during publication (fragile), change the documents to describe the two files
      without depending on them being present (best, and honest — they *are* private working
      material), or publish them after all. This wants deciding before anyone reads the
      public repo, because the dangling references land on the governance documents that are
      most of what a visitor would read first.

### Judgement calls to revisit

- **The publish workflow excludes itself.** Published into the public repo it would run
  there on every push, try to publish that repo to itself, and fail for want of the
  secret — a permanent red X on the showcase. Easy to reverse if you would rather show it;
  disabling Actions on the public repo would be the alternative.
- **`build.yml` *is* published**, so the public repo gets its own green CI. Worth
  confirming that is what you want, since it consumes Actions minutes on a public repo
  (free, but noisy).
- **`.idea/` is excluded wholesale**, per your instruction. Note the current `.gitignore`
  only ignores *specific* `.idea` files, which is why 11 of them are tracked.

---

## Phase 1 — Finish what is in flight

The working tree carries UC12 plus a rework of UC09, **uncommitted**. Do not start
anything else until this is closed out.

- [ ] Act on the `ddd-hex-reviewer` and `spec-documenter` verdicts for the UC12/UC09
      increment. Both prior increments returned `DRIFT`; expect findings.
- [x] `documentation/use-cases/uc12-cancel-tour-by-guide.spec.md` → `IMPLEMENTED`, DoD
      ticked against real test names (not the planned ones). Done in the `spec-documenter`
      reconciliation pass — 22/24 boxes; only the two gate-shaped ones remain.
- [x] UC09's two dependency-blocked DoD boxes now close: `CancelTourByGuideRollbackIT`
      exists, and the "only from a driver" half of
      `ContextRegistryTest.guideReachesBookingInport_onlyFromADriver` is live — proven by
      planting a `booking.core.inport` import in `GuideTourController` and watching it fail.
      UC09's spec was also reworked per-tour in the same pass (§ 2, § 3, § 5, § 7, § 8) —
      24/26 boxes.
- [x] `tasks.md` regenerated against this plan — 7 phases, 69 blocks, the two live DoD
      scoreboards preserved and the completed August list summarised into a table with the
      detail left in git history. *6.4.4 now ticked
      (rollback IT exists); 6.4.2/6.4.3 annotated, closing on witnessed gates; 6.3.2 (UC08)
      unchanged.*
- [x] `documentation/notes.md`: move the "UC12's DELETE verb" question to *Closed* — it was
      resolved by the maintainer's POST ruling and UC12 § 9 now reflects it.
- [ ] Commit. Doctrine separately from code, per `file-usage.definition.md` § 5.1, and
      **verify each commit builds standalone before committing** — see the § 5.1 violation
      list for what happens otherwise.

---

## Phase 2 — Prove the harness actually runs

**Highest value in this document.** The headline feature has never executed.

Every increment so far ran the inner loop *by hand*: read the spec, write the RED test,
quote the failure, implement, dispatch both agents, run the gates. That works, and it found
real bugs — but `/loop-uc` itself has never driven an increment end to end. For a
repository whose thesis is loop-driven development, that is the one gap an attendee would
notice.

- [ ] Run `/loop-uc` for real on one small increment. Do not pick something large: the
      objective is to exercise the loop, not to ship a feature. A good candidate is one of
      the Phase 4 enforcement gaps — each is a single ArchUnit rule with a plantable
      violation, which is exactly one RED→GREEN→REFACTOR turn.
- [ ] Capture what it does wrong. A loop that has never run will have defects; finding them
      is the point of this phase, and each one is a better demo artefact than a green run.
- [ ] Verify the **no-progress detector** fires. Point the loop at a deliberately
      unsatisfiable DoD criterion and confirm it reports `BLOCKED` after two iterations
      rather than burning tokens. This is the guard that makes an autonomous loop safe to
      demo live, and it has never been triggered.
- [ ] Verify the ADR halt. Give it an increment that trips an `sdd.playbook.md` § 6 trigger
      and confirm it stops and asks instead of deciding.
- [ ] Then decide honestly whether `/loop-uc` earns its place in the talk, or whether the
      *inner* loop plus the two agents is the real story and the outer loop is scaffolding.
      Either answer is fine; an unexercised skill presented as working is not.

Also never exercised: `/uc-to-plan` and `/execute-task`. `/plan-to-task` has been run.
Worth one pass each, or delete them — a command that does not work is worse than no command.

---

## Phase 3 — Doctrine debt

Rules the review agent flagged as `Undocumented`: decisions that had already governed
several increments while living only in a use-case spec, where the precedent was
unappealable and unenforceable. Two were written down in August (`8e9333c`, `4be55c2`).
These remain.

- [ ] **Attribution in the event type, not a payload field.** `BookingCancelledByUser` vs
      `BookingCancelledByGuide` rather than one event with a `CancelledBy` discriminator.
      Currently justified only in Javadoc and two use-case specs. A third cancelling party
      would have no definition-level rule to consult. Belongs in
      `modelling.definition.md` § Domain Event, with the N=2-is-fine / revisit-at-N=3 note.
- [ ] **When an aggregate method may branch on its caller.** `TourBooking.cancel` varies
      both its state guard and its idempotency by `CancelledBy`; `GuideTour.cancel` is not
      idempotent at all while its booking-side counterpart is. Each asymmetry is defensible
      and separately documented, but there is no rule saying when caller-dependent
      behaviour is modelling and when it is two methods wearing one name.
- [ ] Fix the `Optional<Instant>` vs nullable `Instant` split in commands:
      `guide`'s `StartTourCommand` and `CompleteTourCommand` use `Optional`, every
      `booking` command uses a nullable field with `Optional.ofNullable(...)` at the
      driver. Both cannot be right. `coding-style.definition.md` § 1.4's new exception
      argues for nullable; pick one and make the other conform.
- [ ] **Does § 11 rule 3 bind test code?** `ContextRegistryTest` uses `DO_NOT_INCLUDE_TESTS`,
      encoding "production only", but no document says so. UC12 made the question concrete:
      `CancelTourByGuideIT` imports seven `booking` types — six domain, one outport — from
      inside the `guide` package to seed its fixtures, and it could have gone through
      `booking`'s inport instead. Both readings are defensible; neither is written down.
- [ ] **Where does an integration-failure exception live?** `BookingCancellationFailedException`
      sits in `guide.core.domain.guidetour.exception`, which § 3 defines as "domain exceptions
      for this aggregate" — and it is not a `GuideTour` rule violation. The placement is
      effectively forced, because § 6 rule 3 lets `inbound.rest` import only from that
      package, so the handler could not map it from anywhere else. Doctrine leaves no
      compliant alternative, which is itself the gap.
- [ ] `CancelTourBookingUseCase` omits `InvalidBookingRequestException` from its `@throws`
      although `CancellationReason` throws it (§ 7.1/§ 7.3).
      `MarkBookingCancelledByGuideUseCase` inherited the omission.

---

## Phase 4 — Turn conventions into enforcement

Each item is a rule the codebase *follows* with nothing stopping it from stopping. These
make the best `/loop-uc` candidates in Phase 2: small, testable, with a plantable violation.

- [ ] **§ 8.1's REST half is unenforced.** A REST-reachable command must carry no
      timestamp. `CancelTourBookingCommand` complies; nothing fails if a future one does
      not. Needs an ArchUnit rule: no command referenced from `inbound.rest` may declare an
      `Instant` component.
- [ ] **`reconstitute` overload selection is unenforced.** `ClassRoleRulesTest` constrains
      *which classes* may call it, not *which overload* they pick, so a mapper could reach
      for the short form and silently drop cancellation attribution. Both aggregates'
      Javadoc now says so honestly; the persistence ITs are what actually catch it. Either
      mechanise it or record it as accepted.
- [x] **The reason ceiling is now pinned on both sides.** Closed during the UC12 review.
      `CancelTourByGuideDriver.MAX_REASON_LENGTH` is gone — the rule moved into
      `guide.core.domain.guidetour.CancellationReason`, because `guide` stores the reason and
      so owns the invariant. Each context pins its own constant against its own column:
      `TourBookingJooqRepositoryIT.cancellationReasonColumnWidth_matchesTheDomainCeiling` and
      `GuideTourJooqRepositoryIT.cancellationReasonColumnWidth_matchesTheDomainCeiling`. The
      cross-context parity is deliberately *not* asserted by importing the other context's
      constant — that relocates the dependency into test code rather than removing it, and it
      would lean on the unanswered question of whether § 11 rule 3 binds tests. Each side
      duplicates the literal `400` instead, so a drift is caught on the side that caused it.
- [ ] Consider an ArchUnit rule for the § 10 cross-context transaction conditions — at
      minimum, that no `@Transactional(REQUIRES_NEW)` sits on a driver reachable from
      another context's driver.

---

## Phase 5 — Test-depth gaps

- [ ] **No Spring integration test asserts `@TransactionalEventListener(AFTER_COMMIT)` +
      `REQUIRES_NEW` for the UC06/UC07 listeners.** Both listener tests document this
      omission honestly and both are Spring-free by choice, but the result is that ADR-0002's
      central guarantee — a rollback cannot leak an event — is verified nowhere. UC12's
      `CancelTourByGuideRollbackIT` is the template: boot the full app, fail one side, assert
      the other did not commit.
- [ ] `CancelTourByGuideIT` and `CancelTourByGuideRollbackIT` are the only tests that boot
      the whole `AlpineBookingApplication`. Everything else stubs its collaborators or scopes
      component scanning, so a broken bean graph is invisible outside those two. One
      context-loads smoke test would be cheap insurance.
- [ ] No test asserts the fan-out is transactional per batch rather than per booking. UC09
      cancels N bookings in one transaction; nothing proves booking 7 failing rolls back
      bookings 1–6.
- [ ] **`CancelTourByGuideRollbackIT` mocks the booking inport**, so it proves `guide`'s
      writes roll back but never that `booking`'s do. Switching the callee to
      `REQUIRES_NEW` would break `architecture.definition.md` § 10 condition 3 and **no test
      would fail**. Needs a test where the real callee succeeds on booking 1 and fails on
      booking 2, asserting booking 1 is still CONFIRMED. Found by `ddd-hex-reviewer`; this is
      the most valuable single test left in the repository.

---

## Phase 6 — Domain gaps (deliberately last)

Real, documented, and **not worth doing for the demo** unless a specific talk needs them.
Each is recorded as a Known Gap in the relevant port spec, which is itself the point: a
showcase that names its gaps is more credible than one that looks finished.

- [ ] **No optimistic locking.** Two concurrent cancellations can both read a non-terminal
      state and both write. Today `markCompleted`/`markActive` idempotency absorbs the
      duplicate, so the *aggregate guard* contains this, not the persistence layer. Adding a
      version column is an ADR (persistence strategy, `sdd.playbook.md` § 6 item 4). Would
      make a good ADR-writing demo.
- [ ] **No read side.** `outbound.persistence.read` exists in the package ontology with
      nothing in it; every use case is a command, so half the CQRS structure the
      architecture describes is never exercised. One query use case would light it up.
      Recorded in `notes.md`.
- [ ] **No outbox.** The UC06/UC07 fan-outs are single `REQUIRES_NEW` transactions with no
      retry. `architecture.definition.md` § 10 records this as accepted, with the reasoning.
- [ ] `notes.md` still asks whether an aggregate should hold its own `List<DomainEvent>`.

---

## Definition of Done for this plan

- [ ] Phase 0 committed, working tree clean.
- [ ] `/loop-uc` has driven at least one increment end to end, and its defects are either
      fixed or written down.
- [ ] The no-progress detector and the ADR halt have both been observed firing.
- [ ] `ddd-hex-reviewer` returns `PASS` on the full tree with `Undocumented: none`.
- [ ] `spec-documenter` reports no `Gaps` and no unresolved `Conflicts`.
- [ ] `./gradlew clean test build` green; `app/data/` absent; every commit builds standalone.
- [ ] Every `Class.method` citation in `documentation/` resolves to a test that exists —
      script it, do not eyeball it. Planned names in `SPECIFIED` specs are the only
      exception, and there should be none left.

---

## Working notes for a cold start

Written down because this project has now been picked up cold twice, and the same things
cost time both times.

- **Trust the agents over the test suite.** Green tests hid four real defects for five
  months. Both agents have found errors in code written minutes earlier.
- **A `DRIFT` verdict blocks the increment.** It is not advisory. Twice it was correct about
  something that looked finished.
- **Verify a claim before writing it in a comment.** Three times in August a Javadoc or
  spec asserted a verification that had not been run, and twice the assertion was false.
  Run the mutation, then write the sentence.
- **Mutation-test anything that passes first try.** A test written after the code it covers
  is a coverage claim, not a test, until a deliberate break makes it fail.
- **Enumerated lists in specs go stale; stated criteria do not.** This bit twice — a port
  spec listing the fields `update` writes, and a doctrine rule listing the types allowed a
  nullable component. Both were replaced with obligations.
- **Check `git diff --cached` before committing.** One commit bundled a staged deletion from
  an earlier `git rm` and did not compile.
