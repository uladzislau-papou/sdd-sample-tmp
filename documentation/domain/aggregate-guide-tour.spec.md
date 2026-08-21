# Domain Specification – GuideTour

## Purpose

The guide-side record of a tour session: when it was scheduled, when it actually
started, and what state its execution is in.

`GuideTour` is deliberately **independent of `TourBooking`**. Bookings are
customer-side reservations owned by the `booking` context; a guide tour is the
execution owned by the `guide` context. The two never reference each other's
aggregates — they communicate through `shared.domain.event` (ADR-0002, ADR-0003).

SDD: `documentation/use-cases/uc05-start-tour.spec.md` and
`uc11-complete-tour.spec.md` (implemented), `uc12-cancel-tour-by-guide.spec.md`
(specified).


## 1. Aggregate Root

Name: `GuideTour`
Package: `guide.core.domain.guidetour`

Description: A scheduled tour execution. Owns its own lifecycle and emits an
integration event when it starts so other contexts can react.

Identity: `GuideTourId` — a record wrapping a non-null `UUID`, with a `generate()`
factory. Owned by this context, therefore a Value Object
(ADR-0005, `modelling.definition.md` § Identity).

State:

| Field | Type | Mutable | Notes |
|-------|------|---------|-------|
| `id` | `GuideTourId` | no | |
| `tourId` | `TourId` | no | External catalogue reference. Lives in `shared.domain` because no context in this system owns it — ADR-0005 category 3 |
| `scheduledStart` | `Instant` | no | |
| `status` | `GuideTourStatus` | yes | via transition methods only |
| `startedAt` | `Instant` | yes | exposed as `Optional<Instant>` — empty until started (G-04) |
| `completedAt` | `Instant` | yes | exposed as `Optional<Instant>` — empty until completed (UC11) |

Consistency boundary: the aggregate itself. No cross-aggregate invariant — a guide
tour knows nothing about how many bookings reference it.


## 2. Invariants (Always-Valid)

Enforced today:

- **I-01** — `GuideTourId` MUST NOT wrap a null `UUID`. Enforced in the
  `GuideTourId` record constructor.
- **I-02** — `TourId` MUST NOT be null or blank. Enforced in the `TourId` record
  constructor.
- **I-03** — A tour MUST NOT start before `scheduledStart`.
  Enforced in `start(Instant)`; violation → `TourStartTooEarlyException`.
- **I-04** — A tour MUST NOT start unless it is `SCHEDULED`.
  Enforced in `start(Instant)`; violation → `InvalidGuideTourStateException`.
- **I-05** — `status`, `startedAt` and `completedAt` MUST only change through transition
  methods. Enforced structurally: all three are private with no setters.
- **I-07** — A tour MUST NOT complete before it started.
  Enforced in `complete(Instant)`; violation → `TourCompletedBeforeStartException`.
  **Depends on I-06:** the comparison needs a non-null `startedAt` to compare against,
  so I-07 is only as strong as I-06. `complete(...)` therefore guards the dependency
  explicitly rather than assuming it — see below.
- **I-08** — A tour MUST NOT complete unless it is `RUNNING`.
  Enforced in `complete(Instant)`; violation → `InvalidGuideTourStateException`.
- **I-06** — `startedAt` is non-null if and only if the tour has left `SCHEDULED`
  via `start(...)`. An emergent property of `start(...)` rather than a checked
  invariant: `reconstitute(...)` accepts `RUNNING` with a null `startedAt`, and
  `guide_tour.started_at` is nullable with no CHECK constraint, so the database
  does not enforce it either. `complete(...)` consequently checks it and throws
  `IllegalStateException` naming the aggregate — a data-integrity fault, not a
  business-rule violation (`coding-style.definition.md` § 6.2). Before that guard
  existed, the pairing surfaced as a bare `NullPointerException`, i.e. a 500.
  Covered by `GuideTourTest.complete_throwsIllegalStateException_whenRunningWithoutStartedAt`.

Violations MUST result in an exception.

### Enforcement gaps — all four closed

Kept as history, because one of the four was closed by changing the enforcement
mechanism rather than the code.

- **G-01 — closed.** `schedule(...)` performed **no** null checks; `schedule(null, null,
  null)` yielded a live object, while `TourBooking.request` validated its arguments. Now
  guarded by `Objects.requireNonNull` on all three parameters. Covered by
  `GuideTourTest.schedule_throwsNullPointerException_when{Id,TourId,ScheduledStart}IsNull`.
- **G-02 — closed.** `start(null)` threw `NullPointerException` from
  `startedAt.isBefore(...)` — the right exception *type* by accident, from the wrong place
  and with no message. Now an explicit `Objects.requireNonNull`. `NullPointerException` is
  correct: nulls are programmer errors, not business semantics
  (`coding-style.definition.md` § 6.2). Covered by
  `GuideTourTest.start_throwsNullPointerException_whenStartedAtIsNull`.
- **G-03 — closed by a rule, not by code.** `reconstitute(...)` must stay `public` — the
  mapper is in another package — so visibility cannot express the restriction.
  `ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers` now fails the build if
  anything outside `..outbound.persistence..` calls it, verified by planting a call in
  `StartTourDriver`. Tests still use it as a builder; the importer excludes them.
- **G-04 — closed.** `startedAt()` returned `null` before the tour started, against
  `coding-style.definition.md` § 1.4. Now `Optional<Instant>`, which also simplified
  `GuideTourMapper` and `GuideTourJooqRepository` — both had been wrapping the nullable
  return in `Optional.ofNullable` at the call site.

`BookingActivated.guideTourId()` stays nullable, deliberately: a **record component is a
field, not a query**. `Optional` is for return values, and Java's own guidance discourages
`Optional` fields — so an event carrying an absent correlation id is not a § 1.4 violation.
(`StartTourCommand`'s `Optional<Instant>` component is the inconsistency, in the other
direction.)


## 3. State Model

Possible states (`GuideTourStatus`):
- `SCHEDULED` — created, not yet started
- `RUNNING` — execution under way
- `FINISHED` — execution completed
- `CANCELLED` — called off, before or during execution

Allowed transitions:
- `SCHEDULED → RUNNING` — implemented (`start`, UC05)
- `RUNNING → FINISHED` — implemented (`complete`, UC11)
- `SCHEDULED → CANCELLED` — **specified only** (`cancel`, UC12 — not implemented)
- `RUNNING → CANCELLED` — **specified only** (`cancel`, UC12 — not implemented)

Illegal transitions:
- `RUNNING → RUNNING`, `FINISHED → RUNNING`, `CANCELLED → RUNNING` — all rejected
  by `start` with `InvalidGuideTourStateException`
- `FINISHED → CANCELLED` — a completed tour cannot be retroactively cancelled (UC12 § 4)
- Anything out of `FINISHED` or `CANCELLED` — both are terminal

> **Implementation status.** `start(...)` and `complete(...)` are implemented, so
> `SCHEDULED`, `RUNNING` and `FINISHED` are all reachable. `CANCELLED` is not yet — UC12
> (`cancel`) makes it so. It exists today so `start` and `complete` can reject it and so
> the mapper can round-trip it.


## 4. Behavior

### `schedule(GuideTourId, TourId, Instant)` — static factory

Preconditions: `id`, `tourId` and `scheduledStart` non-null (G-01, enforced).
Postconditions: status `SCHEDULED`, `startedAt()` empty, no pending events.
Emitted events: none. Creation is not an event in this context — contrast
`TourBooking.request`, which emits `TourBookingRequested`.

### `reconstitute(GuideTourId, TourId, Instant, GuideTourStatus, Instant startedAt, Instant completedAt)` — static factory

Preconditions: none by design (rehydration rule). Both timestamps may be null.
Postconditions: the aggregate reflects stored state exactly; no pending events.
Emitted events: none. **Must only be called by the persistence mapper** — enforced by
`ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers` (G-03).

### `start(Instant startedAt)`

Preconditions:
- `status == SCHEDULED` (else `InvalidGuideTourStateException`)
- `startedAt >= scheduledStart` (else `TourStartTooEarlyException`)
- `startedAt` non-null, enforced (G-02)

Postconditions:
- `status == RUNNING`
- `startedAt` set to the supplied value
- On failure, **no state change** — both guards run before any mutation

Emitted events: `TourStarted`.

### `complete(Instant completedAt)` — UC11

Preconditions:
- `completedAt` non-null (explicit `requireNonNull`)
- `status == RUNNING` (else `InvalidGuideTourStateException`)
- `startedAt != null` (else `IllegalStateException` — I-06, unenforceable upstream)
- `completedAt >= startedAt` (else `TourCompletedBeforeStartException`)

Listed in evaluation order, which matters: the `startedAt` check must precede the
comparison it feeds.

Postconditions:
- `status == FINISHED`
- `completedAt` set to the supplied value
- On failure, **no state change** — every guard runs before any mutation

Emitted events: `TourCompleted`.

Note the asymmetry with `start`: `start` compares against `scheduledStart` (the *plan*),
`complete` against `startedAt` (what actually happened). A tour may legitimately start late,
but it can never finish before it began.

### `pullDomainEvents()`

Returns an unmodifiable snapshot of recorded events and clears the internal list.
Calling twice returns an empty list the second time. This is the drain the drivers
use: `guideTour.pullDomainEvents().forEach(domainEventPublisher::publish)`.

### Accessors

`id()`, `tourId()`, `scheduledStart()`, `status()`, `startedAt()`, `completedAt()`.
Read-only; no setters exist. The aggregate is not anemic — `start` and `complete` own the
transition logic and the drivers contain none.


## 5. Domain Events

### `TourStarted`

Package: `shared.domain.event` — **not** `guide.core.domain.guidetour.event`.

Trigger: successful `start(...)`.

Payload:

| Field | Type | Notes |
|-------|------|-------|
| `guideTourId` | `String` | Plain string, not `GuideTourId`. ADR-0005 category 2: `booking` consumes this event and must not import `guide`'s identity type |
| `tourId` | `TourId` | ADR-0005 category 3 — owned by no context |
| `startedAt` | `Instant` | |

Placement rationale: this is a **cross-context integration event**. `booking`
subscribes to it (UC06) via `TourStartedListener`, so it lives in `shared` to avoid
a `booking → guide` dependency (`architecture.definition.md` § 9, § 11 rule 3).
Context-internal events would stay in the context's own `event` package — as
`booking`'s five events do.

Delivery: published post-commit via `DomainEventPublisher` (ADR-0002).

### `TourCompleted` — UC11

Package: `shared.domain.event`, same reasoning as `TourStarted`: `booking` subscribes to it
(UC07) and must not depend on `guide`.

Trigger: successful `complete(...)`.

| Field | Type | Notes |
|-------|------|-------|
| `guideTourId` | `String` | Plain string — ADR-0005 category 2 |
| `tourId` | `TourId` | ADR-0005 category 3 |
| `completedAt` | `Instant` | |

`TourCancelledByGuide` (UC12) will follow the same pattern and does not exist yet.


## 6. Failure Scenarios

| Scenario | Exception | Result |
|----------|-----------|--------|
| Start when not `SCHEDULED` | `InvalidGuideTourStateException` | no state change, no event |
| Start before `scheduledStart` | `TourStartTooEarlyException` | no state change, no event |
| Complete when not `RUNNING` | `InvalidGuideTourStateException` | no state change, no event |
| Complete before `startedAt` | `TourCompletedBeforeStartException` | no state change, no event |
| Complete a `RUNNING` tour with a null `startedAt` | `IllegalStateException` | corrupt-data guard (I-06), message names the aggregate id |
| Load a non-existent tour | `GuideTourNotFoundException` | thrown by the driver, not the aggregate |
| `start(null)` | `NullPointerException` | explicit guard (G-02) — nulls are programmer errors, not business semantics |
| `complete(null)` | `NullPointerException` | explicit guard, same rationale |
| `schedule` with a null argument | `NullPointerException` | explicit guard (G-01) |

All four domain exceptions — `InvalidGuideTourStateException`,
`TourStartTooEarlyException`, `TourCompletedBeforeStartException` and
`GuideTourNotFoundException` — extend `RuntimeException` and carry a message naming the
offending state or time. `GuideTourNotFoundException` lives in the aggregate's
`exception` package but is thrown by the drivers (`StartTourDriver`,
`CompleteTourDriver`), never by the aggregate.

`IllegalStateException` is deliberately **not** one of them. It signals a data fault
rather than a business rule, so it stays a JDK type and maps to a 500 — which is the
honest answer when the stored row is inconsistent.


## 7. Test Requirements

Expressed as checkable items, naming the test that satisfies each.

### Covered
- [x] Transition `SCHEDULED → RUNNING` at exactly `scheduledStart` —
      `GuideTourTest.start_transitionsToRunning_whenStartedAtEqualsScheduledStart`
- [x] Transition after `scheduledStart` —
      `GuideTourTest.start_transitionsToRunning_whenStartedAtIsAfterScheduledStart`
- [x] `startedAt` recorded — `GuideTourTest.start_setsStartedAt`
- [x] I-03 boundary — `GuideTourTest.start_throwsTourStartTooEarlyException_whenBeforeScheduledStart`
- [x] I-03 leaves state untouched — `GuideTourTest.start_tooEarly_doesNotChangeStatus`
- [x] I-04 from each illegal state — `GuideTourTest.start_throwsInvalidGuideTourStateException_whenAlreadyRunning`,
      `.whenFinished`, `.whenCancelled`
- [x] Initial state — `GuideTourTest.schedule_setsInitialStatusToScheduled`,
      `.schedule_hasNoPendingEvents`
- [x] Event emission — `GuideTourTest.start_emitsTourStartedEvent`
- [x] Event drain semantics — `GuideTourTest.pullDomainEvents_returnsEmptyOnSecondCall`
- [x] Persistence round-trip incl. UTC handling — `GuideTourJooqRepositoryIT` (7 methods)

### Closed gaps and later increments
- [x] G-01 `schedule` rejects nulls — three `GuideTourTest.schedule_throwsNullPointerException_*` tests
- [x] G-02 `start(null)` guarded — `GuideTourTest.start_throwsNullPointerException_whenStartedAtIsNull`
- [x] G-03 `reconstitute` restricted — `ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers`
- [x] G-04 `startedAt()` returns `Optional` — `GuideTourTest.startedAt_isEmpty_beforeTheTourStarts`, `.startedAt_isPresent_afterTheTourStarts`
- [x] Orchestration: `StartTourDriverTest` — 14 tests covering happy path, clock
      resolution, not-found, all three illegal states and the too-early guard.
      Verified non-vacuous by mutation testing
- [x] `complete(...)` transitions and `TourCompleted` emission — UC11. Eleven
      `GuideTourTest.complete_*` / `completedAt_*` tests, eleven in `CompleteTourDriverTest`,
      five in `GuideTourControllerTest`, and three persistence ITs
      (`update_changesStatus_andCompletedAt_afterComplete`,
      `completedAt_isEmpty_forATourThatWasNeverCompleted`, plus the UTC assertion)
- [x] I-06 guarded where it is relied upon —
      `GuideTourTest.complete_throwsIllegalStateException_whenRunningWithoutStartedAt`.
      Found by `ddd-hex-reviewer` during the UC11 review, not by the original tests
- [ ] `cancel(...)` transitions and `TourCancelledByGuide` emission — UC12, not implemented
