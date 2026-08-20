# Use Case Specification – MarkBookingActive (TourBooking)

## Status
IMPLEMENTED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger/Caller: `guide` — publishes `TourStarted` (in `shared.domain.event`) after a guide starts a tour (UC05).
Integration pattern: event-driven; `booking` listens via `TourStartedListener` (`@TransactionalEventListener`).

## Purpose

Transition all CONFIRMED bookings for a given tour to ACTIVE when the guide starts
tour execution. If a booking is already ACTIVE the call is a no-op (idempotent).


## 1. Intent

Keep booking state consistent with tour execution without coupling the contexts:
`booking` reacts to a guide-side fact rather than being commanded by `guide`.

This use case is **deliberately not exposed via REST**. Only guides can initiate
the transition, and the architecture enforces that through the event boundary
rather than through authorization logic — there is no HTTP endpoint to guard.


## 2. Input Contract

Fields (from the `TourStarted` integration event, not from a client):
- `tourId` — identifies which tour's bookings to activate
- `startedAt` — the actual tour start time, taken from the guide tour
- `guideTourId` — correlation id linking booking activation to the guide tour execution

Validation rules:
- The event is only received `AFTER_COMMIT` of the guide tour transaction (ADR-0002)
- `startedAt` defaults to `ClockPort.now()` when the event carries no value


## 3. Output Contract

Return type:
- `status` (String – `"ACTIVE"`), per activated booking, via `MarkBookingActiveResult`

Error types:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `BookingNotFoundException` | booking disappeared between confirmation and tour start | no-op, logged |
| `InvalidBookingStateException` | booking is CANCELLED or COMPLETED | domain invariant violation |

No HTTP status mapping — this use case has no REST surface.


## 4. Preconditions

- Booking exists
- Booking status is `CONFIRMED` (already-`ACTIVE` is tolerated as a no-op)


## 5. Flow

1. `TourStartedListener` receives the `TourStarted` event (`AFTER_COMMIT`, new transaction)
2. Load all `TourBooking` aggregates for `event.tourId()`
3. For each booking in CONFIRMED status:
   1. `booking.markActive(startedAt, guideTourId)`
   2. Persist via `TourBookingRepository.update(booking)`
   3. Publish `BookingActivated`


## 6. Side Effects

- Persistence: one update per CONFIRMED booking
- Event publication: `BookingActivated` per activated booking


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given a CONFIRMED booking for tour T
When the guide starts tour T and `TourStarted` fires
Then the booking status becomes ACTIVE
And `BookingActivated` is published

**AC-02 – Idempotent Re-Delivery**
Given a booking already in ACTIVE state
When MarkBookingActive is executed again
Then the status remains ACTIVE, no update is persisted and no event is published

**AC-03 – Explicit vs Clock-Supplied Start Time**
Given a CONFIRMED booking
When MarkBookingActive is executed without an explicit `startedAt`
Then `ClockPort` supplies the value; when supplied explicitly, that value is used

**AC-04 – Non-Activatable State**
Given a booking in CANCELLED or COMPLETED state
When MarkBookingActive is executed
Then `InvalidBookingStateException` is thrown and the status is unchanged

**AC-05 – Booking Not Found**
Given no booking exists for the given id
When MarkBookingActive is executed
Then `BookingNotFoundException` is thrown and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | Handling |
|----------|-----------|----------|
| Booking not found | `BookingNotFoundException` | no-op — booking may have been cancelled between confirmation and tour start |
| Booking already ACTIVE | none | idempotent no-op: no update, no event |
| Booking CANCELLED or COMPLETED | `InvalidBookingStateException` | filtered out before processing; not activated |


## 9. REST Contract

`Not applicable — event-driven.`

Triggered exclusively by `shared.domain.event.TourStarted`, published after the
guide tour's transaction commits (see UC05 and ADR-0002). Deliberately has no
endpoint, therefore no `rest/uc06-*.http` file is required.


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `TourBookingTest.markActive_happyPath_transitionsToActive`,
      `MarkBookingActiveDriverTest.markActive_happyPath_returnsActiveStatus`,
      `MarkBookingActiveDriverTest.markActive_happyPath_callsUpdateOnRepository`
- [x] AC-02 covered by `TourBookingTest.markActive_idempotent_whenAlreadyActive_noEventEmitted`,
      `TourBookingTest.markActive_idempotent_whenAlreadyActive_statusRemainsActive`,
      `MarkBookingActiveDriverTest.markActive_idempotent_whenAlreadyActive_doesNotPublishEvent`,
      `MarkBookingActiveDriverTest.markActive_idempotent_whenAlreadyActive_doesNotCallUpdate`,
      `MarkBookingActiveDriverTest.markActive_idempotent_whenAlreadyActive_returnsActiveStatus`
- [x] AC-03 covered by `MarkBookingActiveDriverTest.markActive_usesClockPort_whenStartedAtIsNull`,
      `MarkBookingActiveDriverTest.markActive_usesProvidedStartedAt_whenNotNull`
- [x] AC-04 covered by `TourBookingTest.markActive_throwsInvalidBookingStateException_whenCancelled`,
      `TourBookingTest.markActive_throwsInvalidBookingStateException_whenCompleted`,
      `MarkBookingActiveDriverTest.markActive_throwsInvalidBookingStateException_whenCancelled`
- [x] AC-05 covered by `MarkBookingActiveDriverTest.markActive_throwsBookingNotFoundException_whenNotFound`
- [x] `BookingActivated` emission covered by
      `TourBookingTest.markActive_happyPath_recordsBookingActivatedEvent`,
      `MarkBookingActiveDriverTest.markActive_happyPath_publishesBookingActivatedEvent`
- [ ] `TourStartedListener` covered by a test — **no test exists** for
      `booking.inbound.listener.TourStartedListener` or `TourBookingEventListener`.
      The fan-out in § 5 step 2 (load *all* bookings for a tour, filter to CONFIRMED)
      is therefore unverified, as is the `AFTER_COMMIT` boundary from ADR-0002

### Contracts
- [x] No `rest/` file required — § 9 is not applicable
- [x] Persistence roundtrip for the ACTIVE transition covered by
      `TourBookingJooqRepositoryIT.update_changesStatus_toActive_afterMarkActive`.
      **Correcting an earlier error in this spec:** a previous revision of this item
      claimed `started_at` and `guide_tour_id` persistence was unverified. In fact
      `TourBooking` holds **no such fields** — `markActive(Instant, String)` takes both
      purely as `BookingActivated` event payload, so there is nothing to persist and
      `tour_booking` correctly has no such columns. Only `status` round-trips, which is
      what the spec's § 6 side effects describe
- [x] Port specs `ports/clock.outport.spec.md`,
      `ports/tour-booking-repository.outport.spec.md` and
      `ports/domain-event-publisher.outport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [ ] `ddd-hex-reviewer` returns `PASS` (not yet run against this use case)
- [ ] Quality gates green (`test.definition.md` § 7)
