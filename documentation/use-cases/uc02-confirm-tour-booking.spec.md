# Use Case Specification – ConfirmTourBooking

## Status
IMPLEMENTED

## Purpose

Confirm an existing tour booking.


## 1. Intent

Transition booking from REQUESTED to CONFIRMED.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)

Validation rules:
- Required
- Must be a valid UUID string
- Provided as a path variable, not a request body


## 3. Output Contract

### Success

HTTP `200 OK`

```json
{ "status": "CONFIRMED" }
```

### Errors

| Error | Exception | HTTP Status | Response body |
|-------|-----------|-------------|---------------|
| Booking not found | `BookingNotFoundException` | `404 Not Found` | `{ "error": "<message>" }` |
| Invalid state transition | `InvalidBookingStateException` | `409 Conflict` | `{ "error": "<message>" }` |


## 4. REST Endpoint

```
POST /api/v1/bookings/{bookingId}/confirm
```

- No request body
- Path variable: `bookingId` (UUID string)
- Success: `200 OK` with `{ "status": "CONFIRMED" }`


## 5. Preconditions

- Booking must exist
- State must be REQUESTED


## 6. Flow

1. Parse `BookingId` from path variable
2. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. Call `booking.confirm()` → throws `InvalidBookingStateException` if state ≠ REQUESTED
4. Persist status change via `TourBookingRepository.update(booking)`
5. Publish `TourBookingConfirmed` via `DomainEventPublisher`
6. Return `{ "status": "CONFIRMED" }`


## 7. Side Effects

- Persistence: status column updated to `CONFIRMED`
- Event publication: `TourBookingConfirmed` published after transaction commit (ADR-0002)


## 8. Acceptance Criteria

Given a booking in REQUESTED state
When `POST /api/v1/bookings/{bookingId}/confirm` is called
Then the response is `200 OK` with `{ "status": "CONFIRMED" }`
And the booking is persisted with status `CONFIRMED`
And a `TourBookingConfirmed` domain event is published after commit


## 9. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking with given ID does not exist | `BookingNotFoundException` | 404 |
| Booking exists but is not in REQUESTED state (e.g. already CONFIRMED) | `InvalidBookingStateException` | 409 |


## 10. Test Requirements

Must include:
- Happy path test (domain, driver, REST, persistence)
- `confirm()` guard: throws `InvalidBookingStateException` when state ≠ REQUESTED
- Not-found guard: throws `BookingNotFoundException` when ID unknown
- Persistence verification: `findById` after `update` returns CONFIRMED status
- Event verification: `TourBookingConfirmed` is published on happy path