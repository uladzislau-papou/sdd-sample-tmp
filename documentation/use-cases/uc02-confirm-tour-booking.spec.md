# Use Case Specification – ConfirmTourBooking

## Status
IMPLEMENTED

## Bounded Context
`booking` — triggered via REST by an external client.

## Purpose

Confirm an existing tour booking.


## 1. Intent

**Source:** `n/a — example use case, written to demonstrate the method`

Transition booking from REQUESTED to CONFIRMED.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)

Validation rules:
- Required
- Must be a valid UUID string
- Provided as a path variable, not a request body


## 3. Output Contract

Return type:
- `status` (String – always `"CONFIRMED"` on success), HTTP `200 OK`

```json
{ "status": "CONFIRMED" }
```

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | booking exists but state ≠ REQUESTED | 409 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- Booking must exist
- State must be REQUESTED


## 5. Flow

1. Parse `BookingId` from path variable
2. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. Call `booking.confirm()` → throws `InvalidBookingStateException` if state ≠ REQUESTED
4. Persist status change via `TourBookingRepository.update(booking)`
5. Publish `TourBookingConfirmed` via `DomainEventPublisher`
6. Return `{ "status": "CONFIRMED" }`


## 6. Side Effects

- Persistence: status column updated to `CONFIRMED`
- Event publication: `TourBookingConfirmed` published after transaction commit (ADR-0002)


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given a booking in REQUESTED state
When `POST /api/v1/bookings/{bookingId}/confirm` is called
Then the response is `200 OK` with `{ "status": "CONFIRMED" }`
And the booking is persisted with status `CONFIRMED`
And a `TourBookingConfirmed` domain event is published after commit

**AC-02 – Booking Not Found**
Given no booking exists for the given id
When confirm is called
Then HTTP 404 is returned and nothing is persisted

**AC-03 – Invalid State Transition**
Given a booking that is not in REQUESTED state (e.g. already CONFIRMED)
When confirm is called
Then HTTP 409 is returned and the status is unchanged


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking with given ID does not exist | `BookingNotFoundException` | 404 |
| Booking exists but is not in REQUESTED state (e.g. already CONFIRMED) | `InvalidBookingStateException` | 409 |


## 9. API Contract

Endpoint:
```
POST /api/v1/bookings/{bookingId}/confirm
```

- No request body
- Path variable: `bookingId` (UUID string)

Response body (200 OK):
```json
{ "status": "CONFIRMED" }
```

HTTP status mapping:
- `200 OK` – booking confirmed
- `404 Not Found` – booking does not exist
- `409 Conflict` – state ≠ REQUESTED


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `TourBookingTest.confirm_transitionsStatus_toConfirmed`,
      `ConfirmTourBookingDriverTest.confirm_returnsConfirmedStatus`,
      `ConfirmTourBookingDriverTest.confirm_updatesTheAggregate`,
      `TourBookingRestControllerTest.confirm_returns200_withConfirmedStatus`
- [x] AC-02 covered by `ConfirmTourBookingDriverTest.confirm_throwsBookingNotFoundException_whenNoBookingHasThatIdentity`,
      `TourBookingRestControllerTest.confirm_returns404_whenTheBookingDoesNotExist`
- [x] AC-03 covered by `TourBookingTest.confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed`,
      `ConfirmTourBookingDriverTest.confirm_propagatesInvalidBookingStateException_andUpdatesNothing`,
      `ConfirmTourBookingDriverTest.confirm_propagatesInvalidBookingStateException_andUpdatesNothing`,
      `TourBookingRestControllerTest.confirm_returns409_whenTheBookingIsNotRequested`
- [x] `TourBookingConfirmed` emission covered by
      `TourBookingTest.confirm_recordsTourBookingConfirmedEvent`,
      `TourBookingTest.confirm_recordsTourBookingConfirmedEvent`,
      `ConfirmTourBookingDriverTest.confirm_usesClockPort_forTheEventTimestamp`

### Contracts
- [x] `api/uc02-confirm-tour-booking.http` covers 200, 404 and 409
- [x] Persistence roundtrip covered by `TourBookingJpaRepositoryIT.update_persistsTheStatusTransition`
- [x] Port specs `ports/tour-booking-repository.outport.spec.md` and
      `ports/domain-event-publisher.outport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [x] `ddd-hex-reviewer` returns `PASS` — full-tree clean bill, `Undocumented: none`
- [x] Quality gates green (`test.definition.md` § 7) — 187 tests, 0 failures, ArchUnit and spotlessCheck included
