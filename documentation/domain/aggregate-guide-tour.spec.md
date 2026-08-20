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
| `startedAt` | `Instant` | yes | `null` until started |

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

### Known gaps in enforcement

Documented rather than silently claimed, per `test.definition.md` § 6 ("if coverage
is intentionally missing, it MUST be documented"). These are **not** invariants the
code enforces today:

- **G-01** — `GuideTour.schedule(id, tourId, scheduledStart)` performs **no null
  checks**. `schedule(null, null, null)` yields a live object. `TourBooking.request`
  by contrast validates its arguments. This is an Always-Valid violation
  (`modelling.definition.md` § Always-Valid Principle, `sdd.playbook.md` § 8).
- **G-02** — `start(null)` throws `NullPointerException` from
  `startedAt.isBefore(...)` rather than a domain exception. The contract says
  "must not be null" but nothing enforces it.
- **G-03** — `reconstitute(...)` deliberately skips invariant checks (documented and
  correct per the rehydration rule, `modelling.definition.md` § Rehydration Rule),
  but it is `public`, so nothing prevents application code from using it to bypass
  I-03/I-04. `GuideTourMapper` is its only intended caller.
- **G-04** — `startedAt()` returns `null` before the tour starts.
  `coding-style.definition.md` § 1.4 forbids returning null from the domain and
  application layers and requires `Optional<T>` for absence. The § 1 table describes
  this as intended, but it is a documented rule violation, not a design choice.
  `BookingActivated.guideTourId()` is nullable for the same reason.
  Found by `ddd-hex-reviewer`.

G-01 and G-02 are candidate work items; they are not covered by any current test.


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

Preconditions: none enforced (see G-01).
Postconditions: status `SCHEDULED`, `startedAt` null, no pending events.
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
- `startedAt` non-null (contract only — see G-02)

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
| `start(null)` | `NullPointerException` | **G-02** — should be a domain exception |
| `schedule` with null arguments | none | **G-01** — should be rejected |

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
- [ ] G-01: `schedule` rejects null arguments — no test, no enforcement
- [ ] G-02: `start(null)` raises a domain exception — no test, no enforcement
- [x] Orchestration: `StartTourDriverTest` — 14 tests covering happy path, clock
      resolution, not-found, all three illegal states and the too-early guard.
      Verified non-vacuous by mutation testing
- [ ] `complete(...)` transitions and `TourCompleted` emission — UC11, not implemented
- [ ] `cancel(...)` transitions and `TourCancelledByGuide` emission — UC12, not implemented
