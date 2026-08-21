# Use Case Specification – MarkBookingCompleted (TourBooking)

## Status
IMPLEMENTED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger/Caller: `guide` — publishes `TourCompleted` (in `shared.domain.event`) after a guide completes a tour (UC11).
Integration pattern: event-driven; `booking` listens via a `TourCompletedListener` (`@TransactionalEventListener`).

> **Trigger exists.** UC11 is implemented: `GuideTour.complete(...)` emits `TourCompleted`
> and `CompleteTourDriver` publishes it post-commit.

## Purpose

Update booking status to COMPLETED when the guide completes the tour execution.


## 1. Intent

Close out a booking once its tour has actually finished, mirroring UC06's
activation pattern on the completion side.


## 2. Input Contract

Fields (from the `TourCompleted` integration event):
- `tourId` — identifies which tour's bookings to complete
- `completedAt` (Instant) — optional; defaults to `ClockPort.now()`
- `guideTourId` — optional correlation id, propagated through to `BookingCompleted`

Validation rules:
- The event is only received `AFTER_COMMIT` of the guide tour transaction (ADR-0002)

`guideTourId` travels the full path: `TourCompleted` → `MarkBookingCompletedCommand` →
`TourBooking.markCompleted(Instant, String)` → `BookingCompleted`. It is a plain
nullable `String`, opaque to this context because `guide` owns the identity
(ADR-0005 category 2), and it is **not** persisted — the same treatment UC06 gives it.


## 3. Output Contract

Return type:
- `status` (String – `"COMPLETED"`), per completed booking, via `MarkBookingCompletedResult`

Error types:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | **thrown**, propagates to the caller |
| `InvalidBookingStateException` | status ∉ {ACTIVE, COMPLETED} | **thrown**, propagates to the caller |

No HTTP status mapping — this use case has no REST surface.

An earlier revision of this table claimed `BookingNotFoundException` was a "no-op,
logged". That was never true: `MarkBookingCompletedDriver` throws it, and
`TourCompletedListener` has neither a `catch` nor a logger. The claim mattered, because
"logged" describes error handling nobody wrote — and along the listener path an escaping
exception aborts the rest of the fan-out batch rather than being absorbed. UC06 § 3
carried the identical false claim and is corrected in the same increment.

Along the listener path both exceptions are in practice unreachable: the bookings come
from `findActiveByTourId` inside the same transaction, so they exist and they are ACTIVE.
They are reachable through the inport, which is a published API any caller may use — the
contract is stated for that caller, not for the listener.


## 4. Preconditions

- Booking exists
- Booking status is `ACTIVE` (already-`COMPLETED` is tolerated as a no-op)

`CONFIRMED → COMPLETED` is **not** permitted. Tolerating it would paper over a
missing `TourStarted` event and let a booking complete a tour it never started.


## 5. Flow

1. `TourCompletedListener` receives the `TourCompleted` event (`AFTER_COMMIT`, new transaction)
2. Load the **ACTIVE** bookings for `event.tourId()` via
   `TourBookingRepository.findActiveByTourId(...)` — the status criterion lives in the
   query, not the listener, for the same two reasons documented in UC06 § 5 (no business
   logic in an inbound adapter; one ineligible booking throwing would roll back the whole
   `REQUIRES_NEW` fan-out)
3. For each of them:
   1. `booking.markCompleted(completedAt, guideTourId)`
   2. Persist via `TourBookingRepository.update(booking)`
   3. Publish `BookingCompleted`


## 6. Side Effects

- Persistence: one update per ACTIVE booking — **`status` only**
- Event publication: `BookingCompleted(bookingId, completedAt, guideTourId)` per completed booking

`completedAt` is **not stored** on `TourBooking`, and `tour_booking` has no such column.
An earlier revision of this section specified one. It was dropped deliberately: no
invariant needs the value, the event carries it, and `markActive` already treats
`startedAt` the same way. Storing one guide-side timestamp and not the other would be an
arbitrary asymmetry between two mirror use cases.


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
| Booking not found | `BookingNotFoundException` | thrown; nothing persisted (see § 3) |
| Booking already COMPLETED | none | idempotent no-op |
| Booking REQUESTED or CONFIRMED | `InvalidBookingStateException` | rejected |
| Booking CANCELLED | `InvalidBookingStateException` | rejected |


## 9. REST Contract

`Not applicable — event-driven.`

Triggered exclusively by `shared.domain.event.TourCompleted`. No endpoint,
therefore no `rest/uc07-*.http` file is required.


## 10. Definition of Done

**Complete.** Every test name cited is the **actual** method name in the tree, verified
by grep — they follow `test.definition.md` § 5.1 and the `markActive_*` convention in
`TourBookingTest`. The three governance boxes closed on witnessed runs, not predicted
ones: a `PASS` verdict on the reviewer's second pass, and a green
`./gradlew clean test build`.

### Behaviour
- [x] AC-01 covered by `TourBookingTest.markCompleted_happyPath_transitionsToCompleted`
      and `MarkBookingCompletedDriverTest.markCompleted_happyPath_returnsCompletedStatus`
- [x] AC-02 covered by `TourBookingTest.markCompleted_idempotent_whenAlreadyCompleted_noEventEmitted`
      and `MarkBookingCompletedDriverTest.markCompleted_idempotent_whenAlreadyCompleted_doesNotCallUpdate`
- [x] AC-03 covered by `MarkBookingCompletedDriverTest.markCompleted_usesClockPort_whenCompletedAtIsNull`
      and `.markCompleted_usesProvidedCompletedAt_whenNotNull`
- [x] AC-04 covered by `TourBookingTest.markCompleted_throwsInvalidBookingStateException_whenConfirmed`
      and `.markCompleted_throwsInvalidBookingStateException_whenRequested`
- [x] AC-05 covered by `TourBookingTest.markCompleted_throwsInvalidBookingStateException_whenCancelled`
- [x] AC-06 covered by `MarkBookingCompletedDriverTest.markCompleted_throwsBookingNotFoundException_whenNotFound`
- [x] `BookingCompleted` emission covered by
      `TourBookingTest.markCompleted_happyPath_recordsBookingCompletedEvent`
      and `MarkBookingCompletedDriverTest.markCompleted_happyPath_publishesBookingCompletedEvent`
- [x] `TourCompletedListener` fan-out (query ACTIVE bookings for the tour, skip the rest)
      covered by `TourCompletedListenerTest` — 9 tests, including
      `.onTourCompleted_completesOnlyActive_whenStatusesAreMixed` and
      `.onTourCompleted_ignoresBookingsForOtherTours`

### Contracts
- [x] No `rest/` file required — § 9 is not applicable
- [x] `TourBooking.markCompleted(Instant, String guideTourId)` exists on the aggregate
- [x] `guideTourId` propagates across all three hops, each with its own test and each
      verified by mutation: `TourCompletedListenerTest.onTourCompleted_propagatesCompletedAtAndGuideTourIdFromEvent`,
      `MarkBookingCompletedDriverTest.markCompleted_carriesGuideTourIdFromCommandOntoPublishedEvent`,
      `TourBookingTest.markCompleted_happyPath_carriesGuideTourIdOntoEvent`, plus
      `.markCompleted_acceptsNullGuideTourId` for the absent case
- [x] `MarkBookingCompletedCommand` / `MarkBookingCompletedResult` / `MarkBookingCompletedUseCase`
      exist in the `core.inport` triple
- [x] `MarkBookingCompletedDriver` exists in `booking.inbound.driver`
- [x] `shared.domain.event.TourCompleted` exists, and UC11 publishes it
- [x] No Flyway migration needed — `completedAt` is event payload only (see § 6)
- [x] Persistence roundtrip covered by
      `TourBookingJooqRepositoryIT.update_changesStatus_toCompleted_afterMarkCompleted`
- [x] `TourBookingRepository.findActiveByTourId` exists, and its jOOQ predicate is covered
      by `TourBookingJooqRepositoryIT.findActiveByTourId_returnsOnlyActiveBookingsForThatTour`
      (the listener's stub cannot catch a wrong column or literal in the generated SQL)
- [x] `documentation/domain/aggregate-tour-booking.spec.md` § 3 state model and § 4
      behaviour updated with the COMPLETED transition

### Governance
- [x] This spec reconciled against the code by `spec-documenter`. Every cited test name
      verified verbatim. Two contradictions were reported upward and **both are now
      resolved**:
      1. § 3/§ 8 claimed not-found was "no-op, logged" while no catch or logger exists in
         `TourCompletedListener` or the driver. The **spec** was wrong; both tables now say
         `thrown`. UC06 § 3 carried the same false claim and was corrected with it.
      2. § 2 listed `guideTourId` as an input that nothing propagated. The **code** was
         wrong; it now travels the full path (see § 2), pinned at each of the three hops.
- [x] `ddd-hex-reviewer` returns `PASS` — on the second pass. The first returned `DRIFT`:
      `MarkBookingCompletedUseCase` carried its `@throws` tags on the type rather than the
      method, reintroducing the exact defect UC11 fixed in `CompleteTourUseCase` earlier in
      this same increment
- [x] Quality gates green (`test.definition.md` § 7) — `./gradlew clean test build`,
      262 tests, 0 failures
