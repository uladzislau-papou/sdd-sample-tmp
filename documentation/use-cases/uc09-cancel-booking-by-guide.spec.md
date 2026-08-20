# Use Case Specification – MarkBookingCancelledByGuide (TourBooking)

## Status
SPECIFIED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger/Caller: `guide` — invokes `booking` when a guide cancels a tour (UC12).
Integration pattern: synchronous outport call; `guide` defines a `BookingCancellationPort`
outport, `booking` provides the implementation.

> **Integration pattern differs from UC06/UC07 deliberately.** Activation and
> completion are *notifications* — `booking` may react whenever it likes, so they are
> event-driven and post-commit. Guide-initiated cancellation is different: the guide
> needs to know the bookings were actually cancelled before reporting the tour
> cancelled, so it is a synchronous call with a return value. Changing this to
> event-driven (or the reverse) is a cross-context interaction model change and an
> ADR trigger (`sdd.playbook.md` § 6, item 10).
>
> **Depends on UC08.** The `CancelledBy` concept and the extended `cancel(...)`
> signature are introduced there. UC09 adds the `GUIDE` attribution and the
> cross-context port.

## Purpose

Cancel a booking because the guide cancelled the tour, recording guide attribution.


## 1. Intent

Let the `guide` context cancel the bookings attached to a tour it is cancelling,
without either context reaching into the other's aggregate.


## 2. Input Contract

Fields (via `BookingCancellationPort`, not via REST):
- `bookingId` — required
- `cancelledAt` (Instant) — optional; defaults to `ClockPort.now()`
- `guideTourId` — optional correlation id
- `reason` (String) — optional, free text

Validation rules:
- `bookingId` required and resolvable
- The port is called inside the guide's transaction; see § 6


## 3. Output Contract

Return type:
- `status` (String – `"CANCELLED"`) via `MarkBookingCancelledByGuideResult`

Error types:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | propagated to caller |
| `InvalidBookingStateException` | status is COMPLETED | propagated to caller |

No HTTP status mapping — reached through an outport, not an endpoint.


## 4. Preconditions

- Booking exists
- Booking status is `CONFIRMED` or `ACTIVE`

`ACTIVE` **is** allowed: a tour aborted mid-execution must be cancellable. This is
the one transition where a guide may override a running tour, and it is the reason
UC08 forbids the participant from doing the same.

`COMPLETED` is rejected — a finished tour cannot be retroactively cancelled.


## 5. Flow

1. `guide` calls `BookingCancellationPort.cancelForTour(...)`
2. `booking`'s adapter loads the aggregate via `TourBookingRepository.findById(bookingId)`
   → throw `BookingNotFoundException` if empty
3. Call `booking.cancel(cancelledAt, CancelledBy.GUIDE, reason)` → throws
   `InvalidBookingStateException` if status is COMPLETED
4. Persist via `TourBookingRepository.update(booking)`
5. Publish `BookingCancelledByGuide` via `DomainEventPublisher`
6. Return the resulting status to the caller


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancelled_by`, `cancellation_reason`,
  `guide_tour_id` columns updated
- Event publication: `BookingCancelledByGuide` published after transaction commit (ADR-0002)

Transaction boundary: the port is invoked **within** the guide's transaction, so a
failure to cancel bookings rolls back the tour cancellation. This is the intended
consistency trade-off and is what distinguishes UC09 from UC06/UC07.


## 7. Acceptance Criteria

**AC-01 – Happy Path from CONFIRMED**
Given a booking in CONFIRMED state
When MarkBookingCancelledByGuide is executed
Then the status becomes CANCELLED with `cancelledBy = GUIDE`
And `BookingCancelledByGuide` is published after commit

**AC-02 – Happy Path from ACTIVE**
Given a booking in ACTIVE state (tour aborted mid-execution)
When MarkBookingCancelledByGuide is executed
Then the status becomes CANCELLED with `cancelledBy = GUIDE`

**AC-03 – Completed Tour**
Given a booking in COMPLETED state
When MarkBookingCancelledByGuide is executed
Then `InvalidBookingStateException` is thrown and the status is unchanged

**AC-04 – Already Cancelled**
Given a booking already in CANCELLED state
When MarkBookingCancelledByGuide is executed
Then the call is an idempotent no-op and the existing attribution is not overwritten

**AC-05 – Correlation Recorded**
Given a cancellation carrying a `guideTourId`
When MarkBookingCancelledByGuide is executed
Then the correlation id is persisted and carried on the emitted event

**AC-06 – Booking Not Found**
Given no booking exists for the given id
When MarkBookingCancelledByGuide is executed
Then `BookingNotFoundException` propagates to the caller and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | Handling |
|----------|-----------|----------|
| Booking does not exist | `BookingNotFoundException` | propagated; guide's transaction rolls back |
| Booking in COMPLETED state | `InvalidBookingStateException` | propagated |
| Booking already CANCELLED | none | idempotent no-op |


## 9. REST Contract

`Not applicable — synchronous outport call.`

Reached through `BookingCancellationPort`, owned by `guide`. No endpoint,
therefore no `rest/uc09-*.http` file is required.


## 10. Definition of Done

Nothing is implemented yet; every item is open. Test names are the **planned** names.

### Behaviour
- [ ] AC-01 covered by `TourBookingTest.cancel_byGuide_fromConfirmed_recordsGuideAttribution`
      and `MarkBookingCancelledByGuideDriverTest.cancel_fromConfirmed_returnsCancelledStatus`
- [ ] AC-02 covered by `TourBookingTest.cancel_byGuide_fromActive_transitionsToCancelled`
- [ ] AC-03 covered by `TourBookingTest.cancel_byGuide_fromCompleted_throwsInvalidBookingStateException`
- [ ] AC-04 covered by `TourBookingTest.cancel_byGuide_whenAlreadyCancelled_doesNotOverwriteAttribution`
      and `MarkBookingCancelledByGuideDriverTest.cancel_idempotent_doesNotCallUpdate`
- [ ] AC-05 covered by `MarkBookingCancelledByGuideDriverTest.cancel_persistsGuideTourIdCorrelation`
- [ ] AC-06 covered by `MarkBookingCancelledByGuideDriverTest.cancel_throwsBookingNotFoundException_whenNotFound`
- [ ] `BookingCancelledByGuide` emission covered by
      `TourBookingTest.cancel_byGuide_publishesBookingCancelledByGuideEvent`
- [ ] Transaction rollback behaviour (§ 6) covered by an integration test that fails the
      booking cancellation and asserts the guide tour cancellation did not commit

### Contracts
- [ ] `BookingCancellationPort` exists in `guide.core.outport` — **owned by `guide`**,
      per `architecture.definition.md` § 4.3 ("the core owns the abstraction")
- [ ] `booking` provides the implementation without importing `guide` types, and
      `guide` does not import `booking` types (`architecture.definition.md` § 11 rule 3)
- [ ] `MarkBookingCancelledByGuideCommand` / `Result` / `UseCase` exist in the
      `booking.core.inport` triple
- [ ] `documentation/ports/booking-cancellation.outport.spec.md` written, covering the
      transaction and idempotency expectations in § 6
      (`execution.playbook.md` § 3.2.3)
- [ ] `BookingCancelledByGuide` event exists in `booking.core.domain.tourbooking.event`
- [ ] Persistence roundtrip covered by
      `TourBookingJooqRepositoryIT.update_persistsGuideCancellationAttribution`
- [ ] `documentation/domain/aggregate-tour-booking.spec.md` § 3 state model updated with
      the ACTIVE → CANCELLED transition

### Governance
- [ ] UC08 implemented first — `CancelledBy` and the extended `cancel(...)` come from there
- [ ] UC12 (CancelTourByGuide, guide side) exists as the caller
- [ ] A synchronous cross-context call inside the caller's transaction is a
      cross-context interaction model decision — **ADR required** before implementing
      (`sdd.playbook.md` § 6, items 5 and 10)
- [ ] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS`
- [ ] Quality gates green (`test.definition.md` § 7)
