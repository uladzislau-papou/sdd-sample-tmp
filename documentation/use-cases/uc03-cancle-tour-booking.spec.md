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

### Success

HTTP `200 OK`

```json
{ "status": "CANCELLED" }
```

### Errors

| Error | Exception | HTTP Status | Response body |
|-------|-----------|-------------|---------------|
| Booking not found | `BookingNotFoundException` | `404 Not Found` | `{ "error": "<message>" }` |
| Invalid state transition | `InvalidBookingStateException` | `409 Conflict` | `{ "error": "<message>" }` |


## 4. REST Endpoint

```
DELETE /api/v1/bookings/{bookingId}
```

- No request body
- Path variable: `bookingId` (UUID string)
- Success: `200 OK` with `{ "status": "CANCELLED" }`


## 5. Preconditions

- Booking must exist
- State must be `REQUESTED` or `CONFIRMED`
  - `ACTIVE`, `COMPLETED`, and `CANCELLED` bookings cannot be cancelled


## 6. Flow

1. Parse `BookingId` from path variable
2. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. Call `booking.cancel(now)` → throws `InvalidBookingStateException` if state ∉ {REQUESTED, CONFIRMED}
4. Persist status change via `TourBookingRepository.update(booking)`
5. Publish `TourBookingCancelled` via `DomainEventPublisher`
6. Return `{ "status": "CANCELLED" }`


## 7. Side Effects

- Persistence: status column updated to `CANCELLED`
- Event publication: `TourBookingCancelled` published after transaction commit (ADR-0002)


## 8. Acceptance Criteria

Given a booking in REQUESTED state
When `DELETE /api/v1/bookings/{bookingId}` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `TourBookingCancelled` domain event is published after commit

Given a booking in CONFIRMED state
When `DELETE /api/v1/bookings/{bookingId}` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `TourBookingCancelled` domain event is published after commit


## 9. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking with given ID does not exist | `BookingNotFoundException` | 404 |
| Booking exists but is in ACTIVE state | `InvalidBookingStateException` | 409 |
| Booking exists but is in COMPLETED state | `InvalidBookingStateException` | 409 |
| Booking exists but is already CANCELLED | `InvalidBookingStateException` | 409 |


## 10. Test Requirements

Must include:
- Happy path test from REQUESTED state (domain, driver, REST, persistence)
- Happy path test from CONFIRMED state (domain, driver)
- `cancel()` guard: throws `InvalidBookingStateException` when state is ACTIVE
- `cancel()` guard: throws `InvalidBookingStateException` when state is COMPLETED
- `cancel()` guard: throws `InvalidBookingStateException` when state is already CANCELLED
- Not-found guard: throws `BookingNotFoundException` when ID unknown
- Persistence verification: `findById` after `update` returns CANCELLED status
- Event verification: `TourBookingCancelled` is published on happy path
