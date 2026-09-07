# Domain Specification – GuideTour

## Purpose

The guide-side record of a tour session: a scheduled execution that a guide starts.

It is deliberately **independent** of the customer-side `TourBooking` aggregates. Neither
loads, references or validates the other; the only link is the `TourStarted` domain event
in `shared.domain.event`. That independence is what `ContextRegistryTest` enforces, and it
is the reason the two contexts could be separated at all.

> **Reduced for the template.** The reference example this repository carries covers one
> use case per side of the event, so this aggregate has `schedule` and `start` and nothing
> else. `complete` (UC11) and `cancel` (UC12) were part of the original and are not part of
> the example — see `project.definition.md` non-goals. A service adding them adds the
> states, the events and the invariants together; the shape below is what to copy.


## 1. Aggregate Root

Name: `GuideTour`
Package: `guide.core.domain.guidetour`

| Property | Type | Mutable | Meaning |
|----------|------|---------|---------|
| `id` | `GuideTourId` | no | identity, owned by this context |
| `tourId` | `TourId` | no | the catalogue tour this executes, from `shared.domain` |
| `scheduledStart` | `Instant` | no | when it was planned to begin |
| `status` | `GuideTourStatus` | yes, `private set` | lifecycle position |
| `startedAt` | `Instant?` | yes, `private set` | when it actually began; null while `SCHEDULED` |

Not a `data class`: structural equality is wrong for an entity, and a generated `copy()`
would let a caller bypass `start` (`coding-style.definition.md` § 2.1).


## 2. Invariants (Always-Valid)

- **I-01** `id`, `tourId` and `scheduledStart` are present and never change.
- **I-02** A tour may only be started from `SCHEDULED`. Starting a `RUNNING` tour raises
  `InvalidGuideTourStateException`.
- **I-03** `startedAt` may not precede `scheduledStart`. Violation raises
  `TourStartTooEarlyException` — a *conflict*, not a bad request: the input is well-formed,
  the world is not ready.
- **I-04** `startedAt` is null if and only if the status is `SCHEDULED`. Absence means "not
  started yet", which is a real state and not missing data.
- **I-05** A rejected transition changes nothing — no status change, no `startedAt`, no
  event. The guards run before any mutation.

Violations raise a domain exception. None of them raise `IllegalArgumentException`: every
value here arrives through an inbound port, so the domain is the last guard guaranteed to
run (`coding-style.definition.md` § 6.2).


## 3. State Model

Possible states:
- `SCHEDULED`
- `RUNNING`

Allowed transitions:
- `SCHEDULED → RUNNING` via `start`

`GuideTourStatus` declares **only these two**. The original also had `FINISHED` and
`CANCELLED`, which nothing in the example can reach; keeping them would force every
exhaustive `when` to carry an unreachable branch, and § 2.3 of the coding style forbids the
`else` that would otherwise hide it.


## 4. Behavior

### `schedule(GuideTourId, TourId, Instant scheduledStart)` — static factory

Creates a tour in `SCHEDULED` with `startedAt` absent and **no pending events**. Scheduling
is not a fact other contexts react to, so it publishes nothing.

### `reconstitute(GuideTourId, TourId, Instant, GuideTourStatus, Instant?)` — static factory

Rebuilds a persisted tour. Re-checks **no** invariants: the row was valid when written, and
re-validating a past fact means a later rule change can make history unreadable. Records no
events.

Callable only from `..outbound.persistence..`, enforced by
`ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence` — any other caller would be
skipping the guards `schedule` and `start` apply.

### `start(Instant startedAt)`

Guards I-02 then I-03, then sets `status = RUNNING`, records `startedAt`, and records
`TourStarted`.

Time arrives as a parameter, never read from a clock here: the driver reads `ClockPort` and
passes the result in (`architecture.definition.md` § 8), which is what makes I-03 testable
at all.

### `pullDomainEvents()`

Returns and clears the recorded events. A second call returns empty. The snapshot is a copy,
so a caller iterating it cannot be surprised by a later transition.


## 5. Domain Events

### `TourStarted`

Package: `shared.domain.event` — **not** `guide.core.domain`. It lives in the shared kernel
so the `booking` context can subscribe without depending on this one.

| Field | Type | Note |
|-------|------|------|
| `guideTourId` | `String` | deliberately a plain string: `shared` may not depend on `guide`, so the identity crosses as an opaque value (ADR-0005) |
| `tourId` | `TourId` | shared-kernel type, so both contexts can read it |
| `startedAt` | `Instant` | the actual start |

Published after the transaction commits (ADR-0002). UC06 is its consumer.


## 6. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Tour is already `RUNNING` | `InvalidGuideTourStateException` | 409 |
| `startedAt` precedes `scheduledStart` | `TourStartTooEarlyException` | 409 |
| No tour with that identity | `GuideTourNotFoundException` (raised by the driver) | 404 |
| Identifier is not a UUID | `IllegalArgumentException` | 400 — backstop, see `coding-style.definition.md` § 6.3 |


## 7. Test Requirements

Every invariant is covered by a named test in `GuideTourTest`:

| Invariant | Test |
|-----------|------|
| I-02 | `GuideTourTest.start_throwsInvalidGuideTourStateException_whenAlreadyRunning` |
| I-03 | `GuideTourTest.start_throwsTourStartTooEarlyException_whenBeforeTheScheduledTime` and `GuideTourTest.start_succeeds_whenStartedExactlyAtTheScheduledTime` — the boundary is inclusive, so both sides of it are asserted |
| I-04 | `GuideTourTest.schedule_leavesStartedAtAbsent` and `GuideTourTest.start_recordsTheActualStartTime` |
| I-05 | `GuideTourTest.start_leavesStateUntouched_whenTooEarly` |

Transition and event coverage:

- `GuideTourTest.schedule_setsStatus_toScheduled`
- `GuideTourTest.schedule_recordsNoEvents`
- `GuideTourTest.start_transitionsStatus_toRunning`
- `GuideTourTest.start_publishesTourStarted_carryingTheIdentityAsAString`
- `GuideTourTest.reconstitute_recordsNoEvents`

Persistence roundtrip: `GuideTourJpaRepositoryIT.save_thenFindById_roundTripsEveryField`,
`GuideTourJpaRepositoryIT.update_persistsTheTransition_andTheStartTime` and
`GuideTourJpaRepositoryIT.startedAt_staysNull_forATourThatWasNeverStarted` — the last one
exists because the mapper converts through `LocalDateTime`, where a careless default would
silently invent a start time and satisfy I-04 in letter while breaking it in fact.

Every citation above is checked by `SpecCitationsTest`, so a renamed test cannot leave this
section quietly claiming coverage that no longer exists.
