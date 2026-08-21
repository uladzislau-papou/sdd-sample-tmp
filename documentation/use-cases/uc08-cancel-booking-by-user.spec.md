# Use Case Specification – CancelBookingByUser (TourBooking)

## Status
IMPLEMENTED

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
>
> **The verb changes: `DELETE` → `POST /{bookingId}/cancel`.** Ruled by the maintainer.
> `DELETE` was already the wrong verb — cancelling is a state transition that leaves the
> booking addressable, not a removal — and carrying a reason makes it indefensible: a
> `DELETE` body is dropped by some clients, proxies and CDNs, so `reason` would vanish
> intermittently and no test in this repo would catch it. `POST` also matches the
> guide-side `POST /guide-tours/{id}/complete` already in the tree. The old `DELETE`
> route is **removed**, not deprecated in place; this is a deliberate breaking change to
> a live endpoint.

## Purpose

Cancel a booking on the participant's initiative, recording who cancelled and why.


## 1. Intent

Make cancellation attributable. The domain currently records *that* a booking was
cancelled but not *by whom*, which makes guide-initiated and user-initiated
cancellations indistinguishable downstream.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)
- `reason` (String) — optional, free text

The cancellation time is **not** an input. The driver reads it from `ClockPort`
(`architecture.definition.md` § 8.1): the time an action happened is the system's
observation, not the caller's claim, and no business case here needs a client to assert it.
An earlier revision accepted `cancelledAt` from the request body, which let a caller date a
cancellation before the booking existed or years into the future — unbounded and
unvalidated. The fix is to accept nothing rather than to validate a range.

Validation rules:
- `bookingId` required, must be a valid UUID string
- `reason` is optional. When supplied it must be non-blank and at most **400
  characters**; both are enforced by the `CancellationReason` value object, so an
  invalid reason cannot reach the aggregate (Always-Valid, `modelling.definition.md`)

400 is a deliberate ceiling, not a guess: it is free text for a human to read, it is
bounded so the column can be `VARCHAR(400)` rather than unbounded `TEXT`, and it is the
one number the spec previously left as "a documented length limit" without documenting
it — an omission that would have shipped as no validation at all.


## 3. Output Contract

Return type:
- `status` (String – always `"CANCELLED"` on success), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `InvalidBookingRequestException` | `reason` is blank or longer than 400 characters | 400 |
| `IllegalArgumentException` | `bookingId` is not a well-formed UUID | 400 |
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | status ∉ {REQUESTED, CONFIRMED} | 409 |

All errors return `{ "error": "<message>" }`.

Both 400s were missing from this table while § 9 already promised the status. That gap is
how validation silently fails to exist: § 3 is the table an implementer works from, so an
unnamed exception means no class, no test, and no enforcement — while § 9 keeps claiming
the status is returned. `InvalidBookingRequestException` already maps to 400 in
`BookingExceptionHandler`, as does `IllegalArgumentException` (the `UUID.fromString`
backstop), so neither needs a new mapping.


## 4. Preconditions

- Booking exists
- Booking status is `REQUESTED` or `CONFIRMED`
- Booking MUST NOT be `ACTIVE` or `COMPLETED` — a tour already under way cannot be
  cancelled by the participant; that is the guide's decision (UC09)


## 5. Flow

1. Parse `BookingId` from path variable
2. Build `CancellationReason` from the request body when `reason` is present → throws
   `InvalidBookingRequestException` if blank or over 400 characters. Absent reason stays
   absent; the value object is simply not constructed
3. Read `cancelledAt` from `ClockPort.now()` — never from the request (§ 8.1)
4. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
5. Call `booking.cancel(cancelledAt, CancelledBy.USER, reason)` → throws
   `InvalidBookingStateException` if state ∉ {REQUESTED, CONFIRMED}
6. Persist via `TourBookingRepository.update(booking)`
7. Publish `BookingCancelledByUser` via `DomainEventPublisher`
8. Return `{ "status": "CANCELLED" }`

Validation precedes loading deliberately: a malformed reason is rejected without a
database round trip, and a 400 never depends on whether the booking happens to exist.


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancelled_by` and `cancellation_reason` columns
  updated. Added by `V4__DDL_add_tour_booking_cancellation.sql`; all three are nullable,
  since a booking that was never cancelled has no values for them
- Event publication: `BookingCancelledByUser` published after transaction commit (ADR-0002)

**Why this stores its timestamp when UC07 does not.** `markCompleted` deliberately
discards `completedAt` (UC07 § 6), so storing `cancelledAt` here needs a reason rather
than a precedent. It is a different kind of fact: completion is the *guide's* fact
arriving from another context, carried on an event for whoever cares, and no booking
invariant needs it. Cancellation is this aggregate's own act — who cancelled and why is
attribution the booking owns, it is the entire point of UC08 (§ 1), and AC-06 depends on
being able to observe that an existing attribution was not overwritten. State the
aggregate must be able to answer questions about belongs in the aggregate.


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

**AC-04 – Cancellation Time Comes From the Clock**
Given a booking in a cancellable state
When CancelBookingByUser is executed
Then `ClockPort` supplies `cancelledAt`
And a `cancelledAt` in the request body has no effect — the DTO has no such field

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

Replaces UC03's route and verb — same transition, one endpoint:
```
POST /api/v1/bookings/{bookingId}/cancel
```

`DELETE /api/v1/bookings/{bookingId}` is **removed**. See the note under
*Bounded Context* for why the verb changed.

Request body (optional, new in UC08):
```json
{ "reason": "Travel plans changed" }
```

`reason` is optional and the body itself may be omitted entirely — a bare
`POST .../cancel` is the UC03 behaviour preserved. No `cancelledAt`: see § 2.

Response body (200 OK):
```json
{ "status": "CANCELLED" }
```

HTTP status mapping:
- `200 OK` – booking cancelled
- `400 Bad Request` – blank or over-long `reason`, or a malformed `bookingId`
- `404 Not Found` – booking does not exist
- `409 Conflict` – state ∉ {REQUESTED, CONFIRMED}

All four are exercised in `rest/uc03-cancel-tour-booking.http`, which is the file for
this endpoint because UC03 owns the route.


## 10. Definition of Done

An earlier revision of this section carried **planned** test names and a planned
driver-test class `CancelBookingByUserDriverTest`. No such class exists: UC08 extends
UC03's flow, so its driver tests live in `CancelTourBookingDriverTest` alongside the
behaviour they extend. The names below are the ones on disk.

### Behaviour
- [x] AC-01 covered by `TourBookingTest.cancel_byUser_fromRequested_recordsUserAttribution`,
      `CancelTourBookingDriverTest.cancel_recordsUserAsTheInitiator`
      and `CancelTourBookingDriverTest.cancel_fromRequested_returnsCancelledStatus`
- [x] AC-02 covered by `TourBookingTest.cancel_byUser_fromConfirmed_recordsUserAttribution`
- [x] AC-03 covered by `TourBookingTest.cancel_byUser_recordsReason`
      and `CancelTourBookingDriverTest.cancel_publishesEventCarryingReason`;
      the no-reason side by `TourBookingTest.cancel_withoutReason_leavesReasonEmpty`
      and `CancelTourBookingDriverTest.cancel_withoutReason_publishesEventWithNullReason`
- [x] AC-04 covered by `CancelTourBookingDriverTest.cancel_usesClockPort_whenCancelledAtIsNull`
      and `.cancel_usesProvidedCancelledAt_whenNotNull`
- [x] AC-05 covered by `TourBookingTest.cancel_fromActive_throwsInvalidBookingStateException`
      and `.cancel_fromCompleted_throwsInvalidBookingStateException`,
      plus `TourBookingControllerTest.cancelBooking_returns409_whenInvalidState`
- [x] AC-06 covered by `TourBookingTest.cancel_whenAlreadyCancelled_doesNotOverwriteAttribution`,
      with `.cancellationFields_areEmpty_beforeCancellation` pinning the observable baseline
- [x] AC-07 covered by `CancelTourBookingDriverTest.cancel_throwsBookingNotFoundException_whenNotFound`
      and `TourBookingControllerTest.cancelBooking_returns404_whenNotFound`
- [x] `BookingCancelledByUser` emission covered by
      `TourBookingTest.cancel_byUser_publishesBookingCancelledByUserEvent`
- [x] Reason validation at the boundary covered by
      `CancelTourBookingDriverTest.cancel_throwsInvalidBookingRequestException_whenReasonIsBlank`,
      `.cancel_throwsInvalidBookingRequestException_whenReasonExceedsMaxLength`,
      `.cancel_validatesReasonBeforeLoadingTheAggregate`,
      `.cancel_invalidReason_persistsNothingAndPublishesNothing`,
      `TourBookingControllerTest.cancelBooking_returns400_whenReasonIsInvalid`,
      and `CancellationReasonTest` (7 tests) on the value object itself

### Contracts
- [x] `CancelledBy` enum (`USER`, `GUIDE`) exists in `booking.core.domain.tourbooking`
- [x] `CancellationReason` value object exists in `booking.core.domain.tourbooking`,
      rejecting blank and over-400-character text with `InvalidBookingRequestException`
      (`CancellationReasonTest`, 7 tests)
- [x] `TourBooking.cancel(Instant, CancelledBy, CancellationReason)` replaces
      `cancel(Instant)`, and every existing caller and test is migrated — no other
      signature remains on the aggregate.
      **Deviation from an earlier revision of this spec**, which specified a `String`
      third parameter: a validated domain concept passed as a primitive is the
      "primitives for domain concepts" anti-pattern `architecture.definition.md` § 10
      names, and it would leave the 400 unenforceable inside the aggregate
- [x] `cancelledAt`, `cancelledBy` and `cancellationReason` are readable on the aggregate
      (`Optional` accessors), so AC-06's "attribution not overwritten" is observable
      rather than merely implied
- [x] `BookingCancelledByUser` exists in `booking.core.domain.tourbooking.event`
- [x] `BookingCancelledByGuide` exists too, and `cancel(..., CancelledBy.GUIDE, ...)`
      emits it (`TourBookingTest.cancel_byGuide_publishesBookingCancelledByGuideEvent`) —
      the aggregate's contract is complete even though UC09's inport and
      driver are not yet built. `TourBookingCancelled` is **retired**: deleted from
      the tree, no consumer, and leaving it would mean two events for one fact
- [x] Flyway `V4__DDL_add_tour_booking_cancellation.sql` adds the three nullable columns
- [x] Persistence roundtrip for attribution covered by
      `TourBookingJooqRepositoryIT.update_persistsCancellationAttribution`, plus
      `.update_persistsCancellation_withoutReason` and
      `.update_persistsCancelledAt_asUtcLocalDateTime`
- [x] `reconstitute` carries the three new fields (10-argument overload; the 7-argument
      convenience overload serves non-cancelled test fixtures), and `TourBookingMapper`
      round-trips them — including the null case, pinned by
      `TourBookingJooqRepositoryIT.findById_returnsEmptyCancellationFields_forALiveBooking`
- [x] **UC03's spec and `rest/uc03-cancel-tour-booking.http` updated** — the endpoint
      is shared, so UC03 § 3, § 5, § 6, § 7 and § 9 changed in this same increment, and
      its DoD was re-verified against disk rather than assumed still green
- [x] `rest/uc03-cancel-tour-booking.http` uses `POST .../cancel` and covers the happy
      path with and without a body, plus 400 (blank reason, over-long reason, malformed
      id), 404 and 409
- [x] `documentation/domain/aggregate-tour-booking.spec.md` § 2a, § 4, § 5, § 6 and § 7
      updated with `CancelledBy`, `CancellationReason`, the new `cancel` signature, the
      two attributed events and the retirement of `TourBookingCancelled`

### Governance
- [x] **No ADR required.** Ruled by the maintainer. Replacing `cancel(Instant)` with
      `cancel(Instant, CancelledBy, CancellationReason)` touches five call sites and
      UC03's endpoint, but it changes neither the architectural layering, the persistence
      strategy, nor a transaction boundary — it widens one aggregate method's signature.
      None of `sdd.playbook.md` § 6's thirteen triggers fires. Recorded here so the
      *absence* of an ADR is a decision rather than an omission
- [x] **The `DELETE` → `POST` change needs no ADR either**, and was also ruled by the
      maintainer. It is a REST verb correction within an existing adapter, not an
      architectural decision: no layering, port, transaction or persistence change. It
      *is* a breaking API change, which is why § 9 records it explicitly instead of
      letting the route quietly differ from the spec
- [x] This spec reconciled against the code by `spec-documenter` — the planned
      driver-test class and five planned test names had drifted from disk and were
      corrected above; no behavioural contradiction found
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7) — awaits a verified run
