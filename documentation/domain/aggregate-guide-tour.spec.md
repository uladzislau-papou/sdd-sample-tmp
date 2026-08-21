# Domain Specification – GuideTour

## Purpose

The guide-side record of a tour session: when it was scheduled, when it actually
started, and what state its execution is in.

`GuideTour` is deliberately **independent of `TourBooking`**. Bookings are
customer-side reservations owned by the `booking` context; a guide tour is the
execution owned by the `guide` context. The two never reference each other's
aggregates — they communicate through `shared.domain.event` (ADR-0002, ADR-0003).

SDD: `documentation/use-cases/uc05-start-tour.spec.md` (implemented),
`uc11-complete-tour.spec.md` and `uc12-cancel-tour-by-guide.spec.md` (specified).


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
- **I-05** — `status` and `startedAt` MUST only change through transition methods.
  Enforced structurally: both fields are private with no setters.
- **I-06** — `startedAt` is non-null if and only if the tour has left `SCHEDULED`
  via `start(...)`. Currently an emergent property of `start(...)` rather than a
  checked invariant.

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
- `RUNNING → FINISHED` — **specified only** (`complete`, UC11 — not implemented)
- `SCHEDULED → CANCELLED` — **specified only** (`cancel`, UC12 — not implemented)
- `RUNNING → CANCELLED` — **specified only** (`cancel`, UC12 — not implemented)

Illegal transitions:
- `RUNNING → RUNNING`, `FINISHED → RUNNING`, `CANCELLED → RUNNING` — all rejected
  by `start` with `InvalidGuideTourStateException`
- `FINISHED → CANCELLED` — a completed tour cannot be retroactively cancelled (UC12 § 4)
- Anything out of `FINISHED` or `CANCELLED` — both are terminal

> **Implementation status.** `GuideTourStatus` declares all four states and its
> Javadoc lists all four transitions, but `GuideTour` implements only `start(...)`.
> `FINISHED` and `CANCELLED` are currently unreachable at runtime — they exist so
> `start` can reject them and so the persistence mapper can round-trip them. UC11
> and UC12 make them reachable.


## 4. Behavior

### `schedule(GuideTourId, TourId, Instant)` — static factory

Preconditions: `id`, `tourId` and `scheduledStart` non-null (G-01, enforced).
Postconditions: status `SCHEDULED`, `startedAt()` empty, no pending events.
Emitted events: none. Creation is not an event in this context — contrast
`TourBooking.request`, which emits `TourBookingRequested`.

### `reconstitute(GuideTourId, TourId, Instant, GuideTourStatus, Instant)` — static factory

Preconditions: none by design (rehydration rule).
Postconditions: the aggregate reflects stored state exactly; no pending events.
Emitted events: none. **Must only be called by the persistence mapper.**

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

### `pullDomainEvents()`

Returns an unmodifiable snapshot of recorded events and clears the internal list.
Calling twice returns an empty list the second time. This is the drain the drivers
use: `guideTour.pullDomainEvents().forEach(domainEventPublisher::publish)`.

### Accessors

`id()`, `tourId()`, `scheduledStart()`, `status()`, `startedAt()`. Read-only; no
setters exist. The aggregate is not anemic — `start` owns the transition logic and
the drivers contain none.


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

`TourCompleted` (UC11) and `TourCancelledByGuide` (UC12) will follow the same
pattern. Neither exists yet.


## 6. Failure Scenarios

| Scenario | Exception | Result |
|----------|-----------|--------|
| Start when not `SCHEDULED` | `InvalidGuideTourStateException` | no state change, no event |
| Start before `scheduledStart` | `TourStartTooEarlyException` | no state change, no event |
| Load a non-existent tour | `GuideTourNotFoundException` | thrown by the driver, not the aggregate |
| `start(null)` | `NullPointerException` | explicit guard (G-02) — nulls are programmer errors, not business semantics |
| `schedule` with a null argument | `NullPointerException` | explicit guard (G-01) |

All three domain exceptions extend `RuntimeException` and carry a message naming the
offending state or time. `GuideTourNotFoundException` lives in the aggregate's
`exception` package but is thrown by `StartTourDriver`, not by the aggregate.


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
- [x] Persistence round-trip incl. UTC handling — `GuideTourJooqRepositoryIT` (5 methods)

### Open
- [x] G-01 `schedule` rejects nulls — three `GuideTourTest.schedule_throwsNullPointerException_*` tests
- [x] G-02 `start(null)` guarded — `GuideTourTest.start_throwsNullPointerException_whenStartedAtIsNull`
- [x] G-03 `reconstitute` restricted — `ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers`
- [x] G-04 `startedAt()` returns `Optional` — `GuideTourTest.startedAt_isEmpty_beforeTheTourStarts`, `.startedAt_isPresent_afterTheTourStarts`
- [x] Orchestration: `StartTourDriverTest` — 14 tests covering happy path, clock
      resolution, not-found, all three illegal states and the too-early guard.
      Verified non-vacuous by mutation testing
- [ ] `complete(...)` transitions and `TourCompleted` emission — UC11, not implemented
- [ ] `cancel(...)` transitions and `TourCancelledByGuide` emission — UC12, not implemented
