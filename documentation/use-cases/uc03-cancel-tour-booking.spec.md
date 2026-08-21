# Use Case Specification – CancelTourBooking

## Status
IMPLEMENTED

## Bounded Context
`booking` — triggered via REST by an external client.

> **Extended by UC08.** This use case owns the cancellation route, and UC08 changed it in
> place rather than adding a second endpoint for the same transition. Two things moved:
> the verb is now `POST .../cancel` instead of `DELETE`, and the cancellation is attributed
> (`cancelledBy = USER`) with an optional reason. `TourBookingCancelled` was retired in
> favour of `BookingCancelledByUser`.
>
> This spec describes the endpoint as it stands. UC08 carries the rationale for each
> change — see `documentation/use-cases/uc08-cancel-booking-by-user.spec.md`.

## Purpose

Cancel an existing tour booking by transitioning it to the CANCELLED state.


## 1. Intent

Transition a booking from `REQUESTED` or `CONFIRMED` to `CANCELLED`.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)
- `reason` (String) — optional, request body; non-blank and at most 400 characters

The cancellation time is **not** an input. The driver reads it from `ClockPort`
(`architecture.definition.md` § 8.1): the time an action happened is the system's
observation, not the caller's claim, and no business case here needs a client to assert it.
An earlier revision accepted `cancelledAt` from the request body, which let a caller date a
cancellation before the booking existed or years into the future — unbounded and
unvalidated. The fix is to accept nothing rather than to validate a range.

Validation rules:
- `bookingId` required, must be a valid UUID string, provided as a path variable
- The request body as a whole is optional — a bare `POST .../cancel` cancels without a
  reason, which is the behaviour this use case had before UC08
- `reason`, when supplied, is validated by the `CancellationReason` value object


## 3. Output Contract

Return type:
- `status` (String – always `"CANCELLED"` on success), HTTP `200 OK`

```json
{ "status": "CANCELLED" }
```

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `InvalidBookingRequestException` | `reason` is blank or longer than 400 characters | 400 |
| `IllegalArgumentException` | `bookingId` is not a well-formed UUID | 400 |
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | state ∉ {REQUESTED, CONFIRMED} | 409 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- Booking must exist
- State must be `REQUESTED` or `CONFIRMED`
  - `ACTIVE`, `COMPLETED`, and `CANCELLED` bookings cannot be cancelled **by the
    participant**. Since UC09 the aggregate's guard is caller-dependent: a guide may
    additionally cancel from `ACTIVE`, and a second guide cancellation is a no-op
    (aggregate spec § 4). This endpoint's behaviour is unchanged


## 5. Flow

1. Parse `BookingId` from path variable
2. Build `CancellationReason` when `reason` is present → throws
   `InvalidBookingRequestException` if blank or over 400 characters. Before loading, so a
   400 costs no database round trip
3. Read `cancelledAt` from `ClockPort.now()` — never from the request (§ 8.1)
4. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
5. Call `booking.cancel(cancelledAt, CancelledBy.USER, reason)` → throws
   `InvalidBookingStateException` if state ∉ {REQUESTED, CONFIRMED}
6. Persist via `TourBookingRepository.update(booking)`
7. Publish `BookingCancelledByUser` via `DomainEventPublisher`
8. Return `{ "status": "CANCELLED" }`


## 6. Side Effects

- Persistence: `status` set to `CANCELLED`, plus `cancelled_at`, `cancelled_by` and
  `cancellation_reason` (added by `V4__DDL_add_tour_booking_cancellation.sql`)
- Event publication: `BookingCancelledByUser` published after transaction commit (ADR-0002)


## 7. Acceptance Criteria

**AC-01 – Happy Path from REQUESTED**
Given a booking in REQUESTED state
When `POST /api/v1/bookings/{bookingId}/cancel` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `BookingCancelledByUser` domain event is published after commit

**AC-02 – Happy Path from CONFIRMED**
Given a booking in CONFIRMED state
When `POST /api/v1/bookings/{bookingId}/cancel` is called
Then the response is `200 OK` with `{ "status": "CANCELLED" }`
And the booking is persisted with status `CANCELLED`
And a `BookingCancelledByUser` domain event is published after commit

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
POST /api/v1/bookings/{bookingId}/cancel
```

- Path variable: `bookingId` (UUID string)
- Request body **optional** (UC08):

```json
{ "reason": "Travel plans changed" }
```

`reason` is optional and the body may be omitted entirely. There is no `cancelledAt`
field; one sent by a client is ignored rather than honoured, pinned by
`TourBookingControllerTest.cancelBooking_ignoresAClientSuppliedCancelledAt`.

Response body (200 OK):
```json
{ "status": "CANCELLED" }
```

HTTP status mapping:
- `200 OK` – booking cancelled
- `400 Bad Request` – blank or over-long `reason`, or a malformed `bookingId`
- `404 Not Found` – booking does not exist
- `409 Conflict` – state ∉ {REQUESTED, CONFIRMED}

All four are exercised in `rest/uc03-cancel-tour-booking.http`.

**Breaking change.** `DELETE /api/v1/bookings/{bookingId}` no longer exists. Any client on
the old route gets a 405. See UC08 § 9 for why the verb changed rather than a second
endpoint being added.


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
- [x] `BookingCancelledByUser` emission covered by
      `TourBookingTest.cancel_byUser_publishesBookingCancelledByUserEvent`,
      `CancelTourBookingDriverTest.cancel_fromRequested_publishesBookingCancelledByUserEvent`,
      `CancelTourBookingDriverTest.cancel_fromConfirmed_publishesBookingCancelledByUserEvent`.
      Renamed from `TourBookingCancelled` by UC08 — see that spec for why the
      undifferentiated event was retired

### Contracts
- [x] `rest/uc03-cancel-tour-booking.http` covers 200 (with and without a body), both
      400s, 404 and 409 — updated by UC08 when the verb changed
- [x] The route is `POST /{bookingId}/cancel`, exercised by
      `TourBookingControllerTest.cancelBooking_returns200_withCancelledStatus` and
      `.cancelBooking_returns200_whenBodyIsAnEmptyObject`. The first sends no body at all,
      the second a literal `{}` — two distinct branches of the controller's normalisation.
      The former `DELETE` route is gone
- [x] Persistence roundtrip covered by
      `TourBookingJooqRepositoryIT.update_changesStatus_toCancelled_afterCancel`
- [x] Port specs `ports/tour-booking-repository.outport.spec.md` and
      `ports/domain-event-publisher.outport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [x] `ddd-hex-reviewer` returns `PASS` — full-tree clean bill, `Undocumented: none`
- [x] Quality gates green (`test.definition.md` § 7) — 187 tests, 0 failures, ArchUnit and spotlessCheck included

**Re-verified during the UC08 increment.** UC08 modified this endpoint, so every box above
was re-checked rather than assumed still green: the verb changed, the event was renamed,
three columns were added, and the `.http` file was rewritten. The gate figures in the last
box are the ones witnessed when UC03 closed; UC08's own DoD carries the current run.
