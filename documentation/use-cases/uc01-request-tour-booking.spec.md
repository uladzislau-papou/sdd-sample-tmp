# Use Case Specification – RequestTourBooking

## Status
IMPLEMENTED

## Bounded Context
`booking` — triggered via REST by an external client.

## Purpose

Create a new tour booking request.


## 1. Intent

Creates a TourBooking in REQUESTED state after validating availability.


## 2. Input Contract

Fields:
- `tourId` (String) – non-blank
- `tourDate` (LocalDate) – non-null, must be in the future
- `participantCount` (int) – >= 1
- `contactName` (String) – non-blank (part of `ParticipantContact`)
- `contactEmail` (String) – non-blank (part of `ParticipantContact`)

Validation rules:
- All fields required
- tourDate must be in the future at creation time (checked by aggregate)
- participantCount >= 1 (checked by `ParticipantCount` value object)


## 3. Output Contract

Return type:
- `bookingId` (String – UUID)
- `status` (String – always `"REQUESTED"` on success)

Error type(s):

| Exception | Condition | HTTP Status |
|---|---|---|
| `InvalidBookingRequestException` | tourDate in the past, or participantCount < 1 | 400 |
| `CapacityExceededException` | participantCount exceeds available capacity | 409 |
| `AvailabilityUnavailableException` | AvailabilityChecker infrastructure failure | 502 |


## 4. Preconditions

- Availability must confirm free capacity


## 5. Flow

1. Validate input (syntactic) – Bean Validation in REST layer
2. Call `AvailabilityChecker` (outport) → returns `AvailableCapacity`
3. Create `TourBooking` aggregate via `TourBooking.request(...)` – enforces domain invariants
4. Persist aggregate via `TourBookingRepository.save(...)`
5. Collect domain events via `booking.pullDomainEvents()`
6. Publish `TourBookingRequested` via `DomainEventPublisher` (post-commit, see ADR 0002)


## 6. Side Effects

- Persistence: `TourBooking` written to `tour_booking` table
- Availability check: read-only call to `AvailabilityChecker`
- Domain event publication: `TourBookingRequested` published after transaction commit


## 7. Acceptance Criteria

**AC-01 – Happy Path**
Given available capacity
When booking is requested with valid input
Then booking is stored in REQUESTED state and `bookingId` is returned with HTTP 201

**AC-02 – Capacity Exceeded**
Given participantCount exceeds available capacity
When booking is requested
Then HTTP 409 is returned and no booking is persisted

**AC-03 – Invalid Participant Count**
Given participantCount < 1
When booking is requested
Then HTTP 400 is returned and no booking is persisted

**AC-04 – Invalid Tour Date**
Given tourDate is in the past
When booking is requested
Then HTTP 400 is returned and no booking is persisted

**AC-05 – Availability Check Failure**
Given the availability checker throws an infrastructure exception
When booking is requested
Then HTTP 502 is returned and no booking is persisted


## 8. Failure Scenarios

- Capacity exceeded → `CapacityExceededException`
- Invalid participant count → `InvalidBookingRequestException`
- Past tour date → `InvalidBookingRequestException`
- External availability failure → `AvailabilityUnavailableException`


## 9. REST Contract

**Endpoint:** `POST /api/v1/bookings`

**Request Body:**
```json
{
  "tourId": "TOUR-42",
  "tourDate": "2026-07-15",
  "participantCount": 3,
  "contactName": "Jane Doe",
  "contactEmail": "jane.doe@example.com"
}
```

**Response Body (201 Created):**
```json
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "REQUESTED"
}
```

**Error Response (all error codes):**
```json
{
  "error": "<human-readable message>"
}
```

HTTP status mapping:
- `200` – not used (POST creates a resource)
- `201` – booking created successfully
- `400` – validation failure (date, count)
- `409` – capacity exceeded
- `502` – availability check infrastructure failure


## 10. Definition of Done

### Behaviour
- [x] AC-01 covered by `TourBookingTest.requestSetsStatusToRequested`,
      `TourBookingTest.requestStoresAllFieldsCorrectly`,
      `RequestTourBookingDriverTest.happyPath_returnsNonNullBookingIdAndStatusRequested`,
      `TourBookingControllerTest.postWithValidBody_returns201WithBookingIdAndStatus`
- [x] AC-02 covered by `TourBookingTest.participantCountExceedingCapacityThrowsCapacityExceededException`,
      `RequestTourBookingDriverTest.capacityExceeded_throwsCapacityExceededExceptionAndSaveNotCalled`,
      `TourBookingControllerTest.postWithCapacityExceeded_returns409`
- [x] AC-03 covered by `ParticipantCountTest.value0Throws`, `ParticipantCountTest.negativeValueThrows`
- [x] AC-04 covered by `TourBookingTest.pastTourDateThrowsInvalidBookingRequestException`,
      `TourDateTest.isInFutureReturnsFalseForYesterday`, `TourDateTest.isInFutureReturnsFalseForToday`
- [x] AC-05 covered by `RequestTourBookingDriverTest.availabilityFailure_propagatesExceptionAndSaveNotCalled`,
      `TourBookingControllerTest.postWithAvailabilityFailure_returns502`
- [x] `TourBooking` invariants covered by `TourBookingTest`; value object invariants by
      `BookingIdTest`, `ParticipantContactTest`, `ParticipantCountTest`, `TourDateTest`
- [x] `TourBookingRequested` emission covered by
      `TourBookingTest.pullDomainEventsReturnsExactlyOneTourBookingRequested`,
      `TourBookingTest.pullDomainEventsCalledTwiceReturnsEmptyListOnSecondCall`,
      `RequestTourBookingDriverTest.happyPath_publisherReceivesExactlyOneTourBookingRequested`
- [x] AC-03 rejected at the REST boundary with 400 —
      `TourBookingControllerTest.postWithParticipantCountBelowMinimum_returns400`
      (Bean Validation `@Min(1)`, a syntactic rule at the boundary)
- [x] AC-04 mapping covered by
      `TourBookingControllerTest.postWithInvalidBookingRequestFromDomain_returns400`.
      "Tour date must be in the future" is **semantic** — it depends on the current
      time — so the domain owns it (`TourDate` + `ClockPort`) and there is deliberately
      no `@Future` on the request DTO, which would duplicate the rule outside the
      domain. The web layer owns only the `InvalidBookingRequestException` → 400
      mapping, which is what this test asserts

### Contracts
- [x] `rest/uc01-request-tour-booking.http` has a request per status in § 9 — 201, 400
      (three variants), 409 and 502. **Two are annotated as not locally reproducible**,
      both because of `StubAvailabilityChecker`: it never fails (so 502 is unreachable)
      and it returns `AvailableCapacity(Integer.MAX_VALUE)` (so no `participantCount`
      can exceed it and 409 is unreachable). Both are reachable only by binding a
      different `AvailabilityChecker` in `bootstrap/BookingConfig`; both are covered by
      unit and web tests
- [x] Persistence roundtrip covered by `TourBookingJooqRepositoryIT.save_persistsAllFields`,
      `.findById_returnsAggregate_afterSave`, `.save_duplicateId_throwsDuplicateKeyException`
- [x] Port specs `ports/availability-checker.outport.spec.md`,
      `ports/tour-booking-repository.outport.spec.md`,
      `ports/domain-event-publisher.outport.spec.md`,
      `ports/request-tour-booking.inport.spec.md` reflect the ports as implemented

### Governance
- [x] Spec sections § 1–9 reconciled against the code on disk
- [x] `ddd-hex-reviewer` returns `PASS` — full-tree clean bill, `Undocumented: none`
- [x] Quality gates green (`test.definition.md` § 7) — 187 tests, 0 failures, ArchUnit and spotlessCheck included