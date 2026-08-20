# Use Case Specification – CancelTourByGuide (Guide)

## Status
SPECIFIED

## Bounded Context
`guide` — triggered via REST by the guide. Calls `booking` synchronously through
`BookingCancellationPort` to cancel the affected bookings (UC09).
Integration pattern: synchronous outport call, inside the guide's transaction.

> Split out of the former `uc10-guide-actions.spec.md`. This is the **guide-side**
> action; UC09 is the booking-side reaction it drives.

## Purpose

Cancel a scheduled or running guide tour execution on the guide's initiative, and
cancel the bookings attached to it.


## 1. Intent

Let a guide call off a tour — before or during execution — and guarantee that no
booking is left believing the tour is still going ahead.


## 2. Input Contract

Fields:
- `guideTourId` — path variable (UUID format, required)
- `cancelledAt` (Instant) — optional request body field; defaults to `ClockPort.now()`
- `reason` (String) — optional, free text

Validation rules:
- `guideTourId` required, must be a valid UUID string
- `reason`, when supplied, must be non-blank and within a documented length limit


## 3. Output Contract

Return type:
- `status` (String – always `"CANCELLED"` on success), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `GuideTourNotFoundException` | no guide tour with the given id | 404 |
| `InvalidGuideTourStateException` | status ∉ {SCHEDULED, RUNNING} | 409 |
| `BookingCancellationFailedException` | the booking side rejected the cancellation | 502 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- `GuideTour` exists
- Status is `SCHEDULED` or `RUNNING`

`FINISHED` is rejected — a completed tour cannot be retroactively cancelled.
`RUNNING` is allowed: aborting a tour mid-execution is the case UC09 AC-02 exists for.


## 5. Flow

1. Parse `GuideTourId` from path variable
2. Resolve `cancelledAt` — from the request body, else `ClockPort.now()`
3. Load aggregate via `GuideTourRepository.findById(...)` → throw `GuideTourNotFoundException` if empty
4. Call `guideTour.cancel(cancelledAt, reason)` → throws `InvalidGuideTourStateException`
   if status ∉ {SCHEDULED, RUNNING}
5. Persist via `GuideTourRepository.update(guideTour)`
6. Call `BookingCancellationPort.cancelForTour(tourId, cancelledAt, guideTourId, reason)`
   **within the same transaction** — a failure here rolls back step 5
7. Publish `TourCancelledByGuide` via `DomainEventPublisher` (post-commit, ADR-0002)
8. Return `{ "status": "CANCELLED" }`


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancellation_reason` columns updated on `guide_tour`
- Synchronous cross-context call: bookings cancelled via `BookingCancellationPort` (UC09)
- Event publication: `TourCancelledByGuide` published after transaction commit (ADR-0002)

Transaction boundary: steps 5 and 6 share one transaction. If the booking side
fails, the tour cancellation does not commit — the two must not diverge. This is a
deliberate consistency choice, and it is why UC09 is synchronous while UC06/UC07
are event-driven.


## 7. Acceptance Criteria

**AC-01 – Happy Path from SCHEDULED**
Given a SCHEDULED GuideTour with confirmed bookings
When CancelTourByGuide is executed
Then the tour status becomes CANCELLED, the bookings are cancelled with
`cancelledBy = GUIDE`, and `TourCancelledByGuide` is published after commit

**AC-02 – Happy Path from RUNNING**
Given a RUNNING GuideTour (tour aborted mid-execution)
When CancelTourByGuide is executed
Then the tour status becomes CANCELLED and the active bookings are cancelled

**AC-03 – Reason Recorded**
Given a cancellation with a reason supplied
When CancelTourByGuide is executed
Then the reason is persisted and passed through to the booking side

**AC-04 – Explicit vs Clock-Supplied Cancellation Time**
Given a cancellable GuideTour
When CancelTourByGuide is executed without an explicit `cancelledAt`
Then `ClockPort` supplies the value; when supplied explicitly, that value is used

**AC-05 – Finished Tour**
Given a FINISHED GuideTour
When CancelTourByGuide is executed
Then HTTP 409 is returned and the status is unchanged

**AC-06 – Already Cancelled**
Given a GuideTour already CANCELLED
When CancelTourByGuide is executed
Then HTTP 409 is returned and no further booking cancellations are attempted

**AC-07 – Booking Side Fails**
Given the booking side rejects the cancellation
When CancelTourByGuide is executed
Then the transaction rolls back, the tour status remains unchanged,
no `TourCancelledByGuide` is published, and HTTP 502 is returned

**AC-08 – Not Found**
Given no GuideTour exists for the given id
When CancelTourByGuide is executed
Then HTTP 404 is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| GuideTour does not exist | `GuideTourNotFoundException` | 404 |
| Status is FINISHED | `InvalidGuideTourStateException` | 409 |
| Status is already CANCELLED | `InvalidGuideTourStateException` | 409 |
| Booking side rejects cancellation | `BookingCancellationFailedException` | 502 |


## 9. REST Contract

Endpoint:
```
DELETE /api/v1/guide-tours/{guideTourId}
```

Request body (optional):
```json
{ "cancelledAt": "2026-07-15T08:00:00Z", "reason": "Severe weather warning" }
```

Response body (200 OK):
```json
{ "status": "CANCELLED" }
```

HTTP status mapping:
- `200 OK` – tour and bookings cancelled
- `400 Bad Request` – blank or over-long `reason`
- `404 Not Found` – guide tour does not exist
- `409 Conflict` – status ∉ {SCHEDULED, RUNNING}
- `502 Bad Gateway` – booking side rejected the cancellation


## 10. Definition of Done

Nothing is implemented yet; every item is open. Test names are the **planned** names.

### Behaviour
- [ ] AC-01 covered by `GuideTourTest.cancel_fromScheduled_transitionsToCancelled`
      and `CancelTourByGuideDriverTest.cancel_fromScheduled_cancelsBookingsViaPort`
- [ ] AC-02 covered by `GuideTourTest.cancel_fromRunning_transitionsToCancelled`
- [ ] AC-03 covered by `GuideTourTest.cancel_persistsReason`
      and `CancelTourByGuideDriverTest.cancel_passesReasonToBookingPort`
- [ ] AC-04 covered by `CancelTourByGuideDriverTest.cancel_usesClockPort_whenCancelledAtIsNull`
      and `.cancel_usesProvidedCancelledAt_whenNotNull`
- [ ] AC-05 covered by `GuideTourTest.cancel_fromFinished_throwsInvalidGuideTourStateException`
      and `GuideTourControllerTest.cancel_returns409_whenFinished`
- [ ] AC-06 covered by `GuideTourTest.cancel_whenAlreadyCancelled_throwsInvalidGuideTourStateException`
      and `CancelTourByGuideDriverTest.cancel_whenAlreadyCancelled_doesNotCallBookingPort`
- [ ] AC-07 covered by `CancelTourByGuideDriverTest.cancel_propagatesBookingCancellationFailure`,
      `GuideTourControllerTest.cancel_returns502_whenBookingSideFails`, and an
      integration test asserting the guide tour status did **not** commit
- [ ] AC-08 covered by `CancelTourByGuideDriverTest.cancel_throwsGuideTourNotFoundException_whenNotFound`
      and `GuideTourControllerTest.cancel_returns404_whenGuideTourNotFound`
- [ ] `TourCancelledByGuide` emission covered by `GuideTourTest.cancel_emitsTourCancelledByGuideEvent`

### Contracts
- [ ] `GuideTour.cancel(Instant, String)` exists on the aggregate
- [ ] `CancelTourByGuideCommand` / `Result` / `UseCase` exist in `guide.core.inport`
- [ ] `BookingCancellationPort` exists in `guide.core.outport`, framework-free, and
      `guide` does not import any `booking` type (`architecture.definition.md` § 11 rule 3)
- [ ] `documentation/ports/booking-cancellation.outport.spec.md` written, covering the
      shared transaction boundary in § 6 (`execution.playbook.md` § 3.2.3)
- [ ] `shared.domain.event.TourCancelledByGuide` exists
- [ ] `rest/uc12-cancel-tour-by-guide.http` covers 200, 400, 404, 409 and 502
- [ ] Flyway migration adds `cancelled_at` and `cancellation_reason` to `guide_tour`
- [ ] Persistence roundtrip covered by
      `GuideTourJooqRepositoryIT.update_changesStatus_toCancelled`
- [ ] `documentation/domain/aggregate-guide-tour.spec.md` written, covering the full
      SCHEDULED → RUNNING → FINISHED / CANCELLED state model

### Governance
- [ ] UC09 implemented as the booking-side counterpart
- [ ] A synchronous cross-context call inside the caller's transaction is a
      cross-context interaction model decision — **ADR required** before implementing
      (`sdd.playbook.md` § 6, items 5 and 10). One ADR should cover UC09 and UC12 together
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
