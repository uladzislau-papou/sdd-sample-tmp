# Use Case Specification – StartTour (Guide)

## Status
IMPLEMENTED

## Bounded Context
`guide` — triggered via REST by the guide. Publishes `TourStarted` to
`shared.domain.event` as a cross-context integration event consumed by `booking`
(UC06). Integration pattern: event-driven, post-commit (ADR-0002).

## Purpose
Start a scheduled guide tour execution.


## 1. Intent

Transition a SCHEDULED `GuideTour` into RUNNING on the tour day, and announce the
fact to other contexts so dependent bookings can be activated.


## 2. Input Contract

Fields:
- `guideTourId` — path variable (UUID format, required)
- `startedAt` (Instant) — optional request body field; defaults to `ClockPort.now()`

Validation rules:
- `guideTourId` required, must be a valid UUID string
- `startedAt`, when supplied, must not be before `scheduledStart`


## 3. Output Contract

Return type:
- `status` (String – always `"RUNNING"` on success), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `GuideTourNotFoundException` | no guide tour with the given id | 404 |
| `InvalidGuideTourStateException` | status ≠ SCHEDULED | 409 |
| `TourStartTooEarlyException` | `startedAt` is before `scheduledStart` | 409 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- `GuideTour` exists
- Status is `SCHEDULED`
- `startedAt >= scheduledStart`


## 5. Flow

1. Parse `GuideTourId` from path variable
2. Resolve `startedAt` — from the request body, else `ClockPort.now()`
3. Load aggregate via `GuideTourRepository.findById(...)` → throw `GuideTourNotFoundException` if empty
4. Call `guideTour.start(startedAt)` → throws `InvalidGuideTourStateException` or `TourStartTooEarlyException`
5. Persist via `GuideTourRepository.update(guideTour)`
6. Publish `TourStarted` via `DomainEventPublisher` (post-commit, ADR-0002)
7. Return `{ "status": "RUNNING" }`


## 6. Side Effects

- Persistence: `status` and `started_at` columns updated
- Event publication: `TourStarted` published after transaction commit (ADR-0002),
  consumed by `booking` via `TourStartedListener` (UC06)


## 7. Acceptance Criteria

**AC-01 – Happy Path, Clock-Supplied Start**
Given a SCHEDULED GuideTour whose `scheduledStart` has passed
When StartTour is executed without an explicit `startedAt`
Then status becomes RUNNING, `startedAt` is set from `ClockPort`
And `TourStarted` is published after commit

**AC-02 – Happy Path, Explicit Start**
Given a SCHEDULED GuideTour
When StartTour is executed with an explicit `startedAt` at or after `scheduledStart`
Then status becomes RUNNING and `startedAt` is the supplied value

**AC-03 – Too Early**
Given a SCHEDULED GuideTour whose `scheduledStart` is in the future
When StartTour is executed
Then HTTP 409 is returned, the status remains SCHEDULED and no event is published

**AC-04 – Invalid State**
Given a GuideTour already RUNNING, FINISHED or CANCELLED
When StartTour is executed
Then HTTP 409 is returned and the status is unchanged

**AC-05 – Not Found**
Given no GuideTour exists for the given id
When StartTour is executed
Then HTTP 404 is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| GuideTour does not exist | `GuideTourNotFoundException` | 404 |
| Status is RUNNING, FINISHED or CANCELLED | `InvalidGuideTourStateException` | 409 |
| `startedAt` before `scheduledStart` | `TourStartTooEarlyException` | 409 |


## 9. REST Contract

Endpoint:
```
POST /api/v1/guide-tours/{guideTourId}/start
```

Request body (optional):
```json
{ "startedAt": "2026-07-15T09:00:00Z" }
```

Response body (200 OK):
```json
{ "status": "RUNNING" }
```

HTTP status mapping:
- `200 OK` – tour started
- `404 Not Found` – guide tour does not exist
- `409 Conflict` – invalid state, or start too early


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `GuideTourTest.start_transitionsToRunning_whenStartedAtEqualsScheduledStart`,
      `GuideTourTest.start_setsStartedAt`,
      `GuideTourControllerTest.start_returns200_withRunningStatus_whenNoBody`
- [x] AC-02 covered by `GuideTourTest.start_transitionsToRunning_whenStartedAtIsAfterScheduledStart`,
      `GuideTourControllerTest.start_returns200_withRunningStatus_whenStartedAtProvided`
- [x] AC-03 covered by `GuideTourTest.start_throwsTourStartTooEarlyException_whenBeforeScheduledStart`,
      `GuideTourTest.start_tooEarly_doesNotChangeStatus`,
      `GuideTourControllerTest.start_returns409_whenTooEarly`
- [x] AC-04 covered by `GuideTourTest.start_throwsInvalidGuideTourStateException_whenAlreadyRunning`,
      `GuideTourTest.start_throwsInvalidGuideTourStateException_whenFinished`,
      `GuideTourTest.start_throwsInvalidGuideTourStateException_whenCancelled`,
      `GuideTourControllerTest.start_returns409_whenInvalidState`
- [x] AC-05 covered by `GuideTourControllerTest.start_returns404_whenGuideTourNotFound`
- [x] `GuideTour` invariants and initial state covered by
      `GuideTourTest.schedule_setsInitialStatusToScheduled`,
      `GuideTourTest.schedule_hasNoPendingEvents`
- [x] `TourStarted` emission covered by `GuideTourTest.start_emitsTourStartedEvent`,
      `GuideTourTest.pullDomainEvents_returnsEmptyOnSecondCall`
- [x] `StartTourDriver` orchestration covered by `StartTourDriverTest` — 14 tests:
      happy path (`start_happyPath_returnsRunningStatus`, `.callsUpdateOnRepository`,
      `.publishesTourStartedEvent`, `start_publishedEventCarriesGuideTourIdAndTourId`),
      clock resolution (`start_usesClockPort_whenStartedAtIsEmpty`,
      `.start_usesProvidedStartedAt_whenPresent`), not-found
      (`start_throwsGuideTourNotFoundException_whenNotFound`, `.doesNotCallUpdate_whenNotFound`),
      invalid state (three `start_throwsInvalidGuideTourStateException_*`,
      `.doesNotCallUpdate_whenStateInvalid`), too-early
      (`start_propagatesTourStartTooEarlyException_whenBeforeScheduledStart`,
      `.doesNotCallUpdate_whenTooEarly`).
      Spring-free, in-line stubs per `test.definition.md` § 2.2.
      **Note:** these passed on first run — `StartTourDriver` was already correct, so
      the gap was coverage, not behaviour (`tdd.definition.md` § 2). Verified
      non-vacuous by mutation: removing the `ClockPort` resolution fails 7 tests and
      removing the `update` call fails `start_happyPath_callsUpdateOnRepository`

### Contracts
- [x] `rest/uc05-start-tour.http` covers 200 (both variants), 404 and both 409 cases
- [x] Persistence roundtrip covered by
      `GuideTourJooqRepositoryIT.update_changesStatus_andStartedAt_afterStart`,
      `.save_persistsAllFields`, `.save_persistsScheduledStart_asUtcLocalDateTime`,
      `.findById_returnsEmpty_whenNotFound`, `.findById_returnsAggregate_afterSave`
- [x] `documentation/domain/aggregate-guide-tour.spec.md` exists — written, covering
      invariants I-01–I-06, the four-state model, `start`/`schedule`/`reconstitute`
      behaviour, and `TourStarted`. Records three enforcement gaps (G-01 `schedule`
      does no null checks, G-02 `start(null)` throws `NullPointerException`,
      G-03 `reconstitute` is public) as open items rather than claiming them enforced
- [x] `guide-tour-repository.outport.spec.md` exists in `documentation/ports/`
- [x] `start-tour.inport.spec.md` exists in `documentation/ports/`

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [ ] `ddd-hex-reviewer` returns `PASS` (not yet run against this use case)
- [ ] Quality gates green (`test.definition.md` § 7)
