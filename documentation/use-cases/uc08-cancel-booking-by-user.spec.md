# Use Case Specification – CancelBookingByUser (TourBooking)

## Status
SPECIFIED

## Bounded Context
`booking` — triggered via REST by the user (the participant).

> **Overlap with UC03.** `DELETE /api/v1/bookings/{bookingId}` is already
> implemented (UC03 CancelTourBooking) and already performs exactly this state
> transition. UC08 is therefore **not a new endpoint** — it is an extension of the
> UC03 endpoint that adds *attribution* (`cancelledBy = USER`) and an optional
> reason, and emits `BookingCancelledByUser` instead of the undifferentiated
> `TourBookingCancelled`.
>
> Together with UC09 (`cancelledBy = GUIDE`) it makes cancellation attributable,
> which is the actual business requirement. Implementing UC08 means **modifying
> UC03's flow**, and UC03's spec and `rest/uc03-*.http` must be updated in the same
> increment. Treating it as an independent use case would produce two endpoints for
> one transition.

## Purpose

Cancel a booking on the participant's initiative, recording who cancelled and why.


## 1. Intent

Make cancellation attributable. The domain currently records *that* a booking was
cancelled but not *by whom*, which makes guide-initiated and user-initiated
cancellations indistinguishable downstream.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)
- `cancelledAt` (Instant) — optional; defaults to `ClockPort.now()`
- `reason` (String) — optional, free text

Validation rules:
- `bookingId` required, must be a valid UUID string
- `reason`, when supplied, must be non-blank and within a documented length limit


## 3. Output Contract

Return type:
- `status` (String – always `"CANCELLED"` on success), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | status ∉ {REQUESTED, CONFIRMED} | 409 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- Booking exists
- Booking status is `REQUESTED` or `CONFIRMED`
- Booking MUST NOT be `ACTIVE` or `COMPLETED` — a tour already under way cannot be
  cancelled by the participant; that is the guide's decision (UC09)


## 5. Flow

1. Parse `BookingId` from path variable
2. Resolve `cancelledAt` — from the request, else `ClockPort.now()`
3. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
4. Call `booking.cancel(cancelledAt, CancelledBy.USER, reason)` → throws
   `InvalidBookingStateException` if state ∉ {REQUESTED, CONFIRMED}
5. Persist via `TourBookingRepository.update(booking)`
6. Publish `BookingCancelledByUser` via `DomainEventPublisher`
7. Return `{ "status": "CANCELLED" }`


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancelled_by` and `cancellation_reason` columns updated
- Event publication: `BookingCancelledByUser` published after transaction commit (ADR-0002)


## 7. Acceptance Criteria

**AC-01 – Happy Path from REQUESTED**
Given a booking in REQUESTED state
When CancelBookingByUser is executed
Then the status becomes CANCELLED with `cancelledBy = USER`
And `BookingCancelledByUser` is published after commit

**AC-02 – Happy Path from CONFIRMED**
Given a booking in CONFIRMED state
When CancelBookingByUser is executed
Then the status becomes CANCELLED with `cancelledBy = USER`

**AC-03 – Reason Recorded**
Given a cancellation with a reason supplied
When CancelBookingByUser is executed
Then the reason is persisted and carried on the emitted event

**AC-04 – Explicit vs Clock-Supplied Cancellation Time**
Given a booking in a cancellable state
When CancelBookingByUser is executed without an explicit `cancelledAt`
Then `ClockPort` supplies the value; when supplied explicitly, that value is used

**AC-05 – Tour Under Way**
Given a booking in ACTIVE or COMPLETED state
When CancelBookingByUser is executed
Then HTTP 409 is returned and the status is unchanged

**AC-06 – Already Cancelled**
Given a booking already in CANCELLED state
When CancelBookingByUser is executed
Then HTTP 409 is returned and the existing attribution is not overwritten

**AC-07 – Booking Not Found**
Given no booking exists for the given id
When CancelBookingByUser is executed
Then HTTP 404 is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking does not exist | `BookingNotFoundException` | 404 |
| Booking in ACTIVE state | `InvalidBookingStateException` | 409 |
| Booking in COMPLETED state | `InvalidBookingStateException` | 409 |
| Booking already CANCELLED | `InvalidBookingStateException` | 409 |


## 9. REST Contract

Extends the existing UC03 endpoint — no new route:
```
DELETE /api/v1/bookings/{bookingId}
```

Request body (optional, new in UC08):
```json
{ "cancelledAt": "2026-07-15T09:00:00Z", "reason": "Travel plans changed" }
```

Response body (200 OK):
```json
{ "status": "CANCELLED" }
```

HTTP status mapping:
- `200 OK` – booking cancelled
- `400 Bad Request` – blank or over-long `reason`
- `404 Not Found` – booking does not exist
- `409 Conflict` – state ∉ {REQUESTED, CONFIRMED}


## 10. Definition of Done

Nothing is implemented yet; every item is open. Test names are the **planned**
names, following the existing `cancel_*` convention in `TourBookingTest`.

### Behaviour
- [ ] AC-01 covered by `TourBookingTest.cancel_byUser_fromRequested_recordsUserAttribution`
      and `CancelBookingByUserDriverTest.cancel_fromRequested_returnsCancelledStatus`
- [ ] AC-02 covered by `TourBookingTest.cancel_byUser_fromConfirmed_recordsUserAttribution`
- [ ] AC-03 covered by `TourBookingTest.cancel_byUser_persistsReason`
      and `CancelBookingByUserDriverTest.cancel_publishesEventCarryingReason`
- [ ] AC-04 covered by `CancelBookingByUserDriverTest.cancel_usesClockPort_whenCancelledAtIsNull`
      and `.cancel_usesProvidedCancelledAt_whenNotNull`
- [ ] AC-05 covered by `TourBookingTest.cancel_byUser_fromActive_throwsInvalidBookingStateException`
      and `.cancel_byUser_fromCompleted_throwsInvalidBookingStateException`,
      plus `TourBookingControllerTest.cancelBooking_returns409_whenActive`
- [ ] AC-06 covered by `TourBookingTest.cancel_byUser_whenAlreadyCancelled_doesNotOverwriteAttribution`
- [ ] AC-07 covered by `CancelBookingByUserDriverTest.cancel_throwsBookingNotFoundException_whenNotFound`
      and `TourBookingControllerTest.cancelBooking_returns404_whenNotFound`
- [ ] `BookingCancelledByUser` emission covered by
      `TourBookingTest.cancel_byUser_publishesBookingCancelledByUserEvent`

### Contracts
- [ ] `CancelledBy` value object or enum exists in `booking.core.domain.tourbooking`
- [ ] `TourBooking.cancel(Instant, CancelledBy, String)` replaces `cancel(Instant)`,
      and every existing caller and test is migrated
- [ ] `BookingCancelledByUser` event exists in `booking.core.domain.tourbooking.event`
- [ ] Flyway migration adds `cancelled_at`, `cancelled_by`, `cancellation_reason` to `tour_booking`
- [ ] Persistence roundtrip for attribution covered by
      `TourBookingJooqRepositoryIT.update_persistsCancellationAttribution`
- [ ] **UC03's spec and `rest/uc03-cancel-tour-booking.http` updated** — the endpoint
      is shared, so UC03 § 3, § 5, § 6 and § 9 change in this same increment
- [ ] `rest/uc03-cancel-tour-booking.http` gains a request with a body carrying a reason
- [ ] `documentation/domain/aggregate-tour-booking.spec.md` § 2a and § 4 updated with
      `CancelledBy` and the new `cancel` signature

### Governance
- [ ] Replacing `cancel(Instant)` changes a published domain method used by UC03 —
      confirm with the user whether this needs an ADR before implementing
      (`sdd.playbook.md` § 6)
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
