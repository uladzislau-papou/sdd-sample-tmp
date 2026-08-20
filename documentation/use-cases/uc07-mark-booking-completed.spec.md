# Use Case Specification – MarkBookingCompleted (TourBooking)

## Status
SPECIFIED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger/Caller: `guide` — publishes `TourCompleted` (in `shared.domain.event`) after a guide completes a tour (UC11).
Integration pattern: event-driven; `booking` listens via a `TourCompletedListener` (`@TransactionalEventListener`).

> **Dependency:** the `TourCompleted` event has no publisher yet. UC11
> (CompleteTour, guide side) must be implemented first, or this use case has no
> trigger. `GuideTour` currently exposes only `start(...)`.

## Purpose

Update booking status to COMPLETED when the guide completes the tour execution.


## 1. Intent

Close out a booking once its tour has actually finished, mirroring UC06's
activation pattern on the completion side.


## 2. Input Contract

Fields (from the `TourCompleted` integration event):
- `tourId` — identifies which tour's bookings to complete
- `completedAt` (Instant) — optional; defaults to `ClockPort.now()`
- `guideTourId` — optional correlation id

Validation rules:
- The event is only received `AFTER_COMMIT` of the guide tour transaction (ADR-0002)


## 3. Output Contract

Return type:
- `status` (String – `"COMPLETED"`), per completed booking, via `MarkBookingCompletedResult`

Error types:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | no-op, logged |
| `InvalidBookingStateException` | status ∉ {ACTIVE, COMPLETED} | domain invariant violation |

No HTTP status mapping — this use case has no REST surface.


## 4. Preconditions

- Booking exists
- Booking status is `ACTIVE` (already-`COMPLETED` is tolerated as a no-op)

`CONFIRMED → COMPLETED` is **not** permitted. Tolerating it would paper over a
missing `TourStarted` event and let a booking complete a tour it never started.


## 5. Flow

1. `TourCompletedListener` receives the `TourCompleted` event (`AFTER_COMMIT`, new transaction)
2. Load all `TourBooking` aggregates for `event.tourId()`
3. For each booking in ACTIVE status:
   1. `booking.markCompleted(completedAt)`
   2. Persist via `TourBookingRepository.update(booking)`
   3. Publish `BookingCompleted`


## 6. Side Effects

- Persistence: one update per ACTIVE booking (`status`, `completed_at`)
- Event publication: `BookingCompleted` per completed booking


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given an ACTIVE booking for tour T
When the guide completes tour T and `TourCompleted` fires
Then the booking status becomes COMPLETED
And `BookingCompleted` is published

**AC-02 – Idempotent Re-Delivery**
Given a booking already in COMPLETED state
When MarkBookingCompleted is executed again
Then the status remains COMPLETED, no update is persisted and no event is published

**AC-03 – Explicit vs Clock-Supplied Completion Time**
Given an ACTIVE booking
When MarkBookingCompleted is executed without an explicit `completedAt`
Then `ClockPort` supplies the value; when supplied explicitly, that value is used

**AC-04 – Not Yet Started**
Given a booking in REQUESTED or CONFIRMED state
When MarkBookingCompleted is executed
Then `InvalidBookingStateException` is thrown and the status is unchanged

**AC-05 – Cancelled Booking**
Given a booking in CANCELLED state
When MarkBookingCompleted is executed
Then `InvalidBookingStateException` is thrown and the status is unchanged

**AC-06 – Booking Not Found**
Given no booking exists for the given id
When MarkBookingCompleted is executed
Then `BookingNotFoundException` is thrown and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | Handling |
|----------|-----------|----------|
| Booking not found | `BookingNotFoundException` | no-op, logged |
| Booking already COMPLETED | none | idempotent no-op |
| Booking REQUESTED or CONFIRMED | `InvalidBookingStateException` | rejected |
| Booking CANCELLED | `InvalidBookingStateException` | rejected |


## 9. REST Contract

`Not applicable — event-driven.`

Triggered exclusively by `shared.domain.event.TourCompleted`. No endpoint,
therefore no `rest/uc07-*.http` file is required.


## 10. Definition of Done

Nothing is implemented yet; every item is open. Test names below are the
**planned** names — they follow `test.definition.md` § 5.1 and the existing
`markActive_*` convention in `TourBookingTest`.

### Behaviour
- [ ] AC-01 covered by `TourBookingTest.markCompleted_happyPath_transitionsToCompleted`
      and `MarkBookingCompletedDriverTest.markCompleted_happyPath_returnsCompletedStatus`
- [ ] AC-02 covered by `TourBookingTest.markCompleted_idempotent_whenAlreadyCompleted_noEventEmitted`
      and `MarkBookingCompletedDriverTest.markCompleted_idempotent_whenAlreadyCompleted_doesNotCallUpdate`
- [ ] AC-03 covered by `MarkBookingCompletedDriverTest.markCompleted_usesClockPort_whenCompletedAtIsNull`
      and `.markCompleted_usesProvidedCompletedAt_whenNotNull`
- [ ] AC-04 covered by `TourBookingTest.markCompleted_throwsInvalidBookingStateException_whenConfirmed`
      and `.markCompleted_throwsInvalidBookingStateException_whenRequested`
- [ ] AC-05 covered by `TourBookingTest.markCompleted_throwsInvalidBookingStateException_whenCancelled`
- [ ] AC-06 covered by `MarkBookingCompletedDriverTest.markCompleted_throwsBookingNotFoundException_whenNotFound`
- [ ] `BookingCompleted` emission covered by
      `TourBookingTest.markCompleted_happyPath_recordsBookingCompletedEvent`
      and `MarkBookingCompletedDriverTest.markCompleted_happyPath_publishesBookingCompletedEvent`
- [ ] `TourCompletedListener` fan-out (load all bookings for a tour, filter to ACTIVE)
      covered by a listener test

### Contracts
- [ ] No `rest/` file required — § 9 is not applicable
- [ ] `TourBooking.markCompleted(Instant)` exists on the aggregate
- [ ] `MarkBookingCompletedCommand` / `MarkBookingCompletedResult` / `MarkBookingCompletedUseCase`
      exist in the `core.inport` triple
- [ ] `MarkBookingCompletedDriver` exists in `booking.inbound.driver`
- [ ] `shared.domain.event.TourCompleted` exists, and UC11 publishes it
- [ ] Flyway migration adds `completed_at` to `tour_booking` (if the timestamp is persisted)
- [ ] Persistence roundtrip covered by `TourBookingJooqRepositoryIT.update_changesStatus_toCompleted`
- [ ] `documentation/domain/aggregate-tour-booking.spec.md` § 3 state model and § 4
      behaviour updated with the COMPLETED transition

### Governance
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
