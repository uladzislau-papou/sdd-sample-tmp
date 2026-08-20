# Use Case Specification – ChangeParticipants

## Status
IMPLEMENTED

## Bounded Context
`booking` — triggered via REST by an external client.

## Purpose

Change the participant count of an existing booking while respecting availability.


## 1. Intent

Update the participant count of a booking, re-checking available capacity so the
aggregate never holds a count the tour cannot accommodate.


## 2. Input Contract

Fields:
- `bookingId` — path variable (UUID format, required)
- `newParticipantCount` (int) — request body, >= 1

Validation rules:
- Both required
- `newParticipantCount >= 1` (enforced by the `ParticipantCount` value object)
- New count must fit within `AvailableCapacity` for the tour and date


## 3. Output Contract

Return type:
- `participantCount` (int – the updated count), HTTP `200 OK`

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `BookingNotFoundException` | no booking with the given id | 404 |
| `InvalidBookingStateException` | state ∉ {REQUESTED, CONFIRMED} | 409 |
| `CapacityExceededException` | new count exceeds available capacity | 409 |
| `AvailabilityUnavailableException` | `AvailabilityChecker` infrastructure failure | 502 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- Booking exists
- State is `REQUESTED` or `CONFIRMED`
- Availability confirms capacity for the new count


## 5. Flow

1. Parse `BookingId` from path variable
2. Load aggregate via `TourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. Call `AvailabilityChecker` (outport) → returns `AvailableCapacity`
4. Call `booking.changeParticipants(newCount, availableCapacity, now)` → throws
   `InvalidBookingStateException` or `CapacityExceededException`. `now` comes from
   `ClockPort`; the domain never calls `Instant.now()`
   (`architecture.definition.md` § 8)
5. Persist via `TourBookingRepository.update(booking)`
6. Publish `ParticipantsChanged` via `DomainEventPublisher`
7. Return the updated participant count


## 6. Side Effects

- Persistence: `update` writes every mutable column — `participant_count`,
  `available_capacity` (refreshed from the availability check) and `status`.
  See `ports/tour-booking-repository.outport.spec.md` § 2.3
- Availability check: read-only call to `AvailabilityChecker`
- Event publication: `ParticipantsChanged` published after transaction commit (ADR-0002)


## 7. Acceptance Criteria

**AC-01 – Increase Within Capacity**
Given a REQUESTED or CONFIRMED booking and sufficient available capacity
When the participant count is increased
Then the booking reflects the new count, `200 OK` is returned
And a `ParticipantsChanged` domain event is published after commit

**AC-02 – Decrease**
Given a REQUESTED or CONFIRMED booking
When the participant count is decreased
Then the booking reflects the new count and `200 OK` is returned

**AC-03 – Booking Not Found**
Given no booking exists for the given id
When the change is requested
Then HTTP 404 is returned and nothing is persisted

**AC-04 – Non-Modifiable State**
Given a booking in CANCELLED (or otherwise non-modifiable) state
When the change is requested
Then HTTP 409 is returned and the count is unchanged

**AC-05 – Capacity Exceeded**
Given a new count that exceeds available capacity
When the change is requested
Then HTTP 409 is returned and the count is unchanged

**AC-06 – Availability Check Failure**
Given the availability checker throws an infrastructure exception
When the change is requested
Then the exception propagates as HTTP 502 and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| Booking does not exist | `BookingNotFoundException` | 404 |
| Booking in CANCELLED state | `InvalidBookingStateException` | 409 |
| New count exceeds available capacity | `CapacityExceededException` | 409 |
| Availability checker unavailable | `AvailabilityUnavailableException` | 502 |


## 9. REST Contract

Endpoint:
```
PATCH /api/v1/bookings/{bookingId}/participants
```

Request body:
```json
{ "newParticipantCount": 5 }
```

Response body (200 OK):
```json
{ "participantCount": 5 }
```

HTTP status mapping:
- `200 OK` – count updated
- `400 Bad Request` – `newParticipantCount` missing or below 1
- `404 Not Found` – booking does not exist
- `409 Conflict` – invalid state, or capacity exceeded
- `502 Bad Gateway` – availability check infrastructure failure


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `TourBookingTest.changeParticipants_increaseCount_updatesParticipantCountAndPublishesEvent`,
      `ChangeParticipantsDriverTest.change_returnsUpdatedParticipantCount`,
      `ChangeParticipantsDriverTest.change_callsUpdateOnRepository`,
      `TourBookingControllerTest.changeParticipants_returns200_withUpdatedCount`
- [x] AC-02 covered by `TourBookingTest.changeParticipants_decreaseCount_updatesParticipantCount`
- [x] AC-03 covered by `ChangeParticipantsDriverTest.change_throwsBookingNotFoundException_whenNotFound`,
      `TourBookingControllerTest.changeParticipants_returns404_whenNotFound`
- [x] AC-04 covered by `TourBookingTest.changeParticipants_fromCancelled_throwsInvalidBookingStateException`,
      `ChangeParticipantsDriverTest.change_throwsInvalidBookingStateException_whenCancelled`,
      `TourBookingControllerTest.changeParticipants_returns409_whenInvalidState`
- [x] AC-05 covered by `TourBookingTest.changeParticipants_exceedingCapacity_throwsCapacityExceededException`,
      `ChangeParticipantsDriverTest.change_throwsCapacityExceededException_whenNewCountExceedsCapacity`,
      `TourBookingControllerTest.changeParticipants_returns409_whenCapacityExceeded`
- [x] AC-06 covered by `ChangeParticipantsDriverTest.change_propagatesAvailabilityUnavailableException`
- [x] `ParticipantsChanged` emission covered by
      `ChangeParticipantsDriverTest.change_publishesParticipantsChangedEvent`
- [x] `400 Bad Request` for `newParticipantCount < 1` covered by
      `TourBookingControllerTest.changeParticipants_returns400_whenCountBelowMinimum`
- [x] `502` mapping covered by
      `TourBookingControllerTest.changeParticipants_returns502_whenAvailabilityUnavailable`

### Contracts
- [x] `rest/uc04-change-participants.http` has a request per status in § 9 — 200, 400,
      404, both 409 variants and 502. **The capacity-exceeded 409 and the 502 are
      annotated as not locally reproducible**: `StubAvailabilityChecker` returns
      `AvailableCapacity(Integer.MAX_VALUE)` and never fails. An earlier comment in that
      file claimed the stub returns 50, which was never true. Both statuses are covered
      by unit and web tests
- [x] Persistence roundtrip for a participant-count change covered by
      `TourBookingJooqRepositoryIT.update_changesParticipantCount_inDatabase` and
      `.update_changesAvailableCapacity_inDatabase`.
      **These found a real bug, not a coverage gap.**
      `TourBookingJooqRepository.update` wrote only the `status` column, so this use
      case's entire effect — the new participant count, and the refreshed available
      capacity — was **never persisted**. It survived because every prior update test
      asserted only `status`. Fixed by adding both columns to the `UPDATE`
- [x] Port specs `ports/availability-checker.outport.spec.md`,
      `ports/tour-booking-repository.outport.spec.md` and
      `ports/domain-event-publisher.outport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [x] `ddd-hex-reviewer` returns `PASS` — full-tree clean bill, `Undocumented: none`
- [x] Quality gates green (`test.definition.md` § 7) — 187 tests, 0 failures, ArchUnit and spotlessCheck included
