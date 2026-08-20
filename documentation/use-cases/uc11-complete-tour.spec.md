# Use Case Specification – CompleteTour (Guide)

## Status
SPECIFIED

## Bounded Context
`guide` — triggered via REST by the guide. Publishes `TourCompleted` to
`shared.domain.event` as a cross-context integration event consumed by `booking`
(UC07). Integration pattern: event-driven, post-commit (ADR-0002).

> Split out of the former `uc10-guide-actions.spec.md`. **UC07 has no trigger until
> this exists** — nothing publishes `TourCompleted` today, and `GuideTour` exposes
> only `start(...)`.

## Purpose

Complete a running guide tour execution.


## 1. Intent

Record that a tour actually finished, and announce it so dependent bookings can be
closed out (UC07). Mirrors UC05 on the completion side.


## 2. Input Contract

Fields:
- `guideTourId` — path variable (UUID format, required)
- `completedAt` (Instant) — optional request body field; defaults to `ClockPort.now()`

Validation rules:
- `guideTourId` required, must be a valid UUID string
- `completedAt`, when supplied, must not be before `startedAt`


## 3. Output Contract

Return type:
- `status` (String – always `"FINISHED"` on success), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `GuideTourNotFoundException` | no guide tour with the given id | 404 |
| `InvalidGuideTourStateException` | status ≠ RUNNING | 409 |
| `TourCompletedBeforeStartException` | `completedAt` is before `startedAt` | 409 |

All errors return `{ "error": "<message>" }`.

`FINISHED` is the terminal status name already used by `GuideTourStatus` — do not
introduce a second name such as `COMPLETED` for the same state.


## 4. Preconditions

- `GuideTour` exists
- Status is `RUNNING`
- `completedAt >= startedAt`


## 5. Flow

1. Parse `GuideTourId` from path variable
2. Resolve `completedAt` — from the request body, else `ClockPort.now()`
3. Load aggregate via `GuideTourRepository.findById(...)` → throw `GuideTourNotFoundException` if empty
4. Call `guideTour.complete(completedAt)` → throws `InvalidGuideTourStateException` or
   `TourCompletedBeforeStartException`
5. Persist via `GuideTourRepository.update(guideTour)`
6. Publish `TourCompleted` via `DomainEventPublisher` (post-commit, ADR-0002)
7. Return `{ "status": "FINISHED" }`


## 6. Side Effects

- Persistence: `status` and `completed_at` columns updated
- Event publication: `TourCompleted` published after transaction commit (ADR-0002),
  consumed by `booking` (UC07)


## 7. Acceptance Criteria

**AC-01 – Happy Path, Clock-Supplied Completion**
Given a RUNNING GuideTour
When CompleteTour is executed without an explicit `completedAt`
Then status becomes FINISHED, `completedAt` is set from `ClockPort`
And `TourCompleted` is published after commit

**AC-02 – Happy Path, Explicit Completion**
Given a RUNNING GuideTour
When CompleteTour is executed with an explicit `completedAt` at or after `startedAt`
Then status becomes FINISHED and `completedAt` is the supplied value

**AC-03 – Completed Before Start**
Given a RUNNING GuideTour
When CompleteTour is executed with `completedAt` before `startedAt`
Then HTTP 409 is returned and the status remains RUNNING

**AC-04 – Not Running**
Given a GuideTour in SCHEDULED, FINISHED or CANCELLED state
When CompleteTour is executed
Then HTTP 409 is returned and the status is unchanged

**AC-05 – Not Found**
Given no GuideTour exists for the given id
When CompleteTour is executed
Then HTTP 404 is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| GuideTour does not exist | `GuideTourNotFoundException` | 404 |
| Status is SCHEDULED, FINISHED or CANCELLED | `InvalidGuideTourStateException` | 409 |
| `completedAt` before `startedAt` | `TourCompletedBeforeStartException` | 409 |


## 9. REST Contract

Endpoint:
```
POST /api/v1/guide-tours/{guideTourId}/complete
```

Request body (optional):
```json
{ "completedAt": "2026-07-15T17:30:00Z" }
```

Response body (200 OK):
```json
{ "status": "FINISHED" }
```

HTTP status mapping:
- `200 OK` – tour completed
- `404 Not Found` – guide tour does not exist
- `409 Conflict` – not RUNNING, or completed before start


## 10. Definition of Done

Nothing is implemented yet; every item is open. Test names are the **planned**
names, following the existing `start_*` convention in `GuideTourTest`.

### Behaviour
- [ ] AC-01 covered by `GuideTourTest.complete_transitionsToFinished`,
      `GuideTourTest.complete_setsCompletedAt`,
      `StartTourDriverTest`'s counterpart `CompleteTourDriverTest.complete_usesClockPort_whenCompletedAtIsNull`,
      and `GuideTourControllerTest.complete_returns200_withFinishedStatus_whenNoBody`
- [ ] AC-02 covered by `CompleteTourDriverTest.complete_usesProvidedCompletedAt_whenNotNull`
      and `GuideTourControllerTest.complete_returns200_withFinishedStatus_whenCompletedAtProvided`
- [ ] AC-03 covered by `GuideTourTest.complete_throwsTourCompletedBeforeStartException_whenBeforeStartedAt`,
      `GuideTourTest.complete_beforeStart_doesNotChangeStatus`,
      and `GuideTourControllerTest.complete_returns409_whenCompletedBeforeStart`
- [ ] AC-04 covered by `GuideTourTest.complete_throwsInvalidGuideTourStateException_whenScheduled`,
      `.complete_throwsInvalidGuideTourStateException_whenAlreadyFinished`,
      `.complete_throwsInvalidGuideTourStateException_whenCancelled`,
      and `GuideTourControllerTest.complete_returns409_whenInvalidState`
- [ ] AC-05 covered by `GuideTourControllerTest.complete_returns404_whenGuideTourNotFound`
- [ ] `TourCompleted` emission covered by `GuideTourTest.complete_emitsTourCompletedEvent`

### Contracts
- [ ] `GuideTour.complete(Instant)` exists on the aggregate
- [ ] `CompleteTourCommand` / `CompleteTourResult` / `CompleteTourUseCase` exist in
      `guide.core.inport`
- [ ] `CompleteTourDriver` exists in `guide.inbound.driver`
- [ ] `shared.domain.event.TourCompleted` exists, mirroring `TourStarted`
- [ ] `GuideTourRestAPI` gains the `@PostMapping("/{guideTourId}/complete")` contract;
      `GuideTourController` carries no HTTP annotations
      (`architecture.definition.md` § 4.5)
- [ ] `rest/uc11-complete-tour.http` covers 200 (both variants), 404 and both 409 cases
- [ ] Flyway migration adds `completed_at` to `guide_tour`
- [ ] Persistence roundtrip covered by
      `GuideTourJooqRepositoryIT.update_changesStatus_andCompletedAt_afterComplete`
- [ ] `documentation/domain/aggregate-guide-tour.spec.md` written — it does not exist
      yet for any guide use case (recorded in `notes.md`)

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
