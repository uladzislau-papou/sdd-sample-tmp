# Use Case Specification – CancelTourBooking

## Status
IMPLEMENTED

## Bounded Context
`booking` — triggered via REST by an external client.

## Purpose

Cancel an existing tour booking by transitioning it to the CANCELLED state.


## 1. Intent

Transition a booking from `REQUESTED` or `CONFIRMED` to `CANCELLED`.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)

Validation rules:
- Required
- Must be a valid UUID string
- Provided as a path variable, not a request body


## 3. Output Contract

Return type:
- `status` (String – always `"CANCELLED"` on success), HTTP `200 OK`

```json
{ "status": "CANCELLED" }
```

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | state ∉ {REQUESTED, CONFIRMED} | 409 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- Booking must exist
- State must be `REQUESTED` or `CONFIRMED`
  - `ACTIVE`, `COMPLETED`, and `CANCELLED` bookings cannot be cancelled


## 5. Flow

1. Parse `BookingId` from path variable
2. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. Call `booking.cancel(now)` → throws `InvalidBookingStateException` if state ∉ {REQUESTED, CONFIRMED}
4. Persist status change via `TourBookingRepository.update(booking)`
5. Publish `TourBookingCancelled` via `DomainEventPublisher`
6. Return `{ "status": "CANCELLED" }`


## 6. Side Effects

- Persistence: status column updated to `CANCELLED`
- Event publication: `TourBookingCancelled` published after transaction commit (ADR-0002)


## 7. Acceptance Criteria

**AC-01 – Happy Path from REQUESTED**
Given a booking in REQUESTED state
When `DELETE /api/v1/bookings/{bookingId}` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `TourBookingCancelled` domain event is published after commit

**AC-02 – Happy Path from CONFIRMED**
Given a booking in CONFIRMED state
When `DELETE /api/v1/bookings/{bookingId}` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `TourBookingCancelled` domain event is published after commit

**AC-03 – Booking Not Found**
Given no booking exists for the given id
When cancel is called
Then HTTP 404 is returned and nothing is persisted

**AC-04 – Non-Cancellable State**
Given a booking in ACTIVE, COMPLETED or already CANCELLED state
When cancel is called
Then HTTP 409 is returned and the status is unchanged


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking with given ID does not exist | `BookingNotFoundException` | 404 |
| Booking exists but is in ACTIVE state | `InvalidBookingStateException` | 409 |
| Booking exists but is in COMPLETED state | `InvalidBookingStateException` | 409 |
| Booking exists but is already CANCELLED | `InvalidBookingStateException` | 409 |


## 9. REST Contract

Endpoint:
```
DELETE /api/v1/bookings/{bookingId}
```

- No request body
- Path variable: `bookingId` (UUID string)

Response body (200 OK):
```json
{ "status": "CANCELLED" }
```

HTTP status mapping:
- `200 OK` – booking cancelled
- `404 Not Found` – booking does not exist
- `409 Conflict` – state ∉ {REQUESTED, CONFIRMED}


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `TourBookingTest.cancel_fromRequested_transitionsToCancelled`,
      `CancelTourBookingDriverTest.cancel_fromRequested_returnsCancelledStatus`,
      `CancelTourBookingDriverTest.cancel_fromRequested_callsUpdateOnRepository`,
      `TourBookingControllerTest.cancelBooking_returns200_withCancelledStatus`
- [x] AC-02 covered by `TourBookingTest.cancel_fromConfirmed_transitionsToCancelled`,
      `CancelTourBookingDriverTest.cancel_fromConfirmed_returnsCancelledStatus`
- [x] AC-03 covered by `CancelTourBookingDriverTest.cancel_throwsBookingNotFoundException_whenNotFound`,
      `TourBookingControllerTest.cancelBooking_returns404_whenNotFound`
- [x] AC-04 covered by `TourBookingTest.cancel_fromActive_throwsInvalidBookingStateException`,
      `TourBookingTest.cancel_fromCompleted_throwsInvalidBookingStateException`,
      `TourBookingTest.cancel_fromCancelled_throwsInvalidBookingStateException`,
      `CancelTourBookingDriverTest.cancel_throwsInvalidBookingStateException_whenActive`,
      `CancelTourBookingDriverTest.cancel_doesNotCallUpdate_whenStateInvalid`,
      `TourBookingControllerTest.cancelBooking_returns409_whenInvalidState`
- [x] `TourBookingCancelled` emission covered by
      `TourBookingTest.cancel_publishesTourBookingCancelledEvent`,
      `CancelTourBookingDriverTest.cancel_fromRequested_publishesTourBookingCancelledEvent`,
      `CancelTourBookingDriverTest.cancel_fromConfirmed_publishesTourBookingCancelledEvent`

### Contracts
- [x] `rest/uc03-cancel-tour-booking.http` covers 200, 404 and 409
- [x] Persistence roundtrip covered by
      `TourBookingJooqRepositoryIT.update_changesStatus_toCancelled_afterCancel`
- [x] Port specs `ports/tour-booking-repository.outport.spec.md` and
      `ports/domain-event-publisher.outport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [ ] `ddd-hex-reviewer` returns `PASS` (not yet run against this use case)
- [ ] Quality gates green (`test.definition.md` § 7)
