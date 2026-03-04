# Plan – UC04: ChangeParticipants

## Status
PLANNING

## Reference
- Spec: `documentation/use-cases/uc04-change-participants.spec.md`
- Architecture: `documentation/architecture.definition.md`
- Domain: `documentation/modelling.definition.md`

---

## 1. Clarifications & Gaps Resolved

### 1.1 HTTP method and path (not defined in spec)
Decision: `PATCH /api/v1/bookings/{bookingId}/participants`
- PATCH signals a partial update of the booking resource
- `/participants` sub-resource makes the intent explicit and avoids clashing with future PATCH /bookings/{id}

### 1.2 Availability delta logic (spec says "Check availability delta")
The `AvailabilityChecker` port returns total available capacity for a tour+date from the external system.
The driver re-checks availability and passes the fresh capacity to `TourBooking.changeParticipants()`.
The domain enforces: `newParticipantCount <= freshAvailableCapacity`.
The updated `availableCapacity` (fresh snapshot) is stored on the aggregate (consistent with UC01 pattern).

If `newParticipantCount <= currentParticipantCount` (decrease), availability is still re-checked and stored
(simple, consistent, avoids special-casing in the driver).

### 1.3 Domain model mutation – participantCount and availableCapacity
Both fields are currently `private final`. The `changeParticipants()` method mutates these.
The existing `status` field already uses `private` (non-final) mutation; applying the same pattern.
Change: remove `final` from `participantCount` and `availableCapacity`; introduce setters only within the aggregate (private field re-assignment in `changeParticipants`).

### 1.4 Response body
Spec says "Return type: updated participantCount". Response: `{ "participantCount": <int> }`.

### 1.5 HTTP status on success
200 OK – consistent with UC02 and UC03 (state transitions return 200).

### 1.6 Error mapping (already handled by BookingExceptionHandler)
- `BookingNotFoundException` → 404 (already mapped)
- `InvalidBookingStateException` → 409 (already mapped)
- `CapacityExceededException` → 409 (already mapped)
- `AvailabilityUnavailableException` → 502 (already mapped)

No changes to `BookingExceptionHandler` needed.

### 1.7 ADR needed?
No. UC04 introduces no new package structure, no new external dependency, no async processing,
no new transaction boundary, no new persistence technology. Follows the established pattern exactly.

---

## 2. Acceptance Criteria

- [ ] `PATCH /api/v1/bookings/{id}/participants` with valid body returns 200 and updated participantCount
- [ ] Attempt on CANCELLED / ACTIVE / COMPLETED booking returns 409
- [ ] Attempt exceeding available capacity returns 409
- [ ] Attempt on non-existent bookingId returns 404
- [ ] `ParticipantsChanged` domain event is published after commit
- [ ] Aggregate is persisted with updated participantCount and availableCapacity
- [ ] `./gradlew clean test` and `./gradlew build` pass

---

## 3. Affected Files

### New files
| File | Purpose |
|------|---------|
| `core/domain/event/ParticipantsChanged.java` | Domain event (record) |
| `core/inport/ChangeParticipantsCommand.java` | Input record: bookingId, newParticipantCount |
| `core/inport/ChangeParticipantsResult.java` | Output record: participantCount |
| `core/inport/ChangeParticipantsUseCase.java` | Inport interface |
| `inbound/driver/ChangeParticipantsDriver.java` | Application service (@Service @Transactional) |
| `inbound/rest/ChangeParticipantsRequest.java` | REST request DTO |
| `inbound/rest/ChangeParticipantsResponse.java` | REST response DTO |
| `rest/uc04-change-participants.http` | HTTP client file (happy path + 3 error cases) |
| `inbound/driver/ChangeParticipantsDriverTest.java` | Use case test (Spring-free) |

### Modified files
| File | Change |
|------|--------|
| `core/domain/TourBooking.java` | Remove `final` from `participantCount`/`availableCapacity`; add `changeParticipants(ParticipantCount, AvailableCapacity, Instant)` |
| `inbound/rest/TourBookingController.java` | Add `PATCH /{bookingId}/participants` endpoint |
| `core/domain/TourBookingTest.java` | Add tests for `changeParticipants()` (happy path, invalid state, capacity exceeded) |
| `inbound/rest/TourBookingControllerTest.java` | Add test cases for new endpoint |

---

## 4. Implementation Steps

### Step 1 – Domain event
Create `core/domain/event/ParticipantsChanged.java`:
```java
record ParticipantsChanged(BookingId bookingId, ParticipantCount newParticipantCount, Instant occurredAt)
    implements DomainEvent {}
```

### Step 2 – Domain method: TourBooking.changeParticipants()
Modify `TourBooking.java`:
- Remove `final` from `participantCount` and `availableCapacity`
- Add method:
  ```java
  public void changeParticipants(ParticipantCount newCount, AvailableCapacity freshCapacity, Instant now) {
      if (status != REQUESTED && status != CONFIRMED) throw new InvalidBookingStateException(status);
      if (newCount.value() > freshCapacity.value()) throw new CapacityExceededException(...);
      this.participantCount = newCount;
      this.availableCapacity = freshCapacity;
      domainEvents.add(new ParticipantsChanged(bookingId, newCount, now));
  }
  ```

### Step 3 – Domain tests
Add to `TourBookingTest.java`:
- changeParticipants happy path (increase and decrease)
- changeParticipants with invalid state (CANCELLED → 409)
- changeParticipants exceeding capacity → CapacityExceededException

### Step 4 – Inport types
Create `ChangeParticipantsCommand`, `ChangeParticipantsResult`, `ChangeParticipantsUseCase` in `core/inport/`.

### Step 5 – Application service (driver)
Create `ChangeParticipantsDriver`:
```
1. Parse bookingId (UUID)
2. Load booking → 404 if absent
3. Get now from ClockPort
4. Call AvailabilityChecker.checkAvailability(tourId, tourDate)
5. Call booking.changeParticipants(newCount, freshCapacity, now)
6. Call repository.update(booking)
7. Publish events via pullDomainEvents()
8. Return ChangeParticipantsResult(booking.participantCount().value())
```

### Step 6 – Driver test
Create `ChangeParticipantsDriverTest.java` (no Spring, stubs/mocks):
- Happy path
- Booking not found → BookingNotFoundException
- Invalid state → InvalidBookingStateException
- Capacity exceeded → CapacityExceededException
- Availability unavailable → AvailabilityUnavailableException

### Step 7 – REST layer
Create `ChangeParticipantsRequest` (with `@NotNull @Min(1) int newParticipantCount`) and
`ChangeParticipantsResponse` (with `int participantCount`).

Add to `TourBookingController`:
```java
@PatchMapping("/{bookingId}/participants")
public ResponseEntity<ChangeParticipantsResponse> changeParticipants(
        @PathVariable String bookingId,
        @Valid @RequestBody ChangeParticipantsRequest request) {
    // delegate to ChangeParticipantsUseCase
    // return 200
}
```

### Step 8 – Controller test
Add test cases to `TourBookingControllerTest.java`:
- 200 happy path
- 404 booking not found
- 409 invalid state
- 409 capacity exceeded

### Step 9 – HTTP client file
Create `rest/uc04-change-participants.http`:
- Happy path request
- 404 error case
- 409 invalid state case
- 409 capacity exceeded case

### Step 10 – Verify
Run `./gradlew clean test` and `./gradlew build`.

---

## 5. Risks

| Risk | Mitigation |
|------|-----------|
| `TourBooking` field mutation (remove `final`) changes reconstitution contract | `reconstitute()` is a static factory that still sets all fields; no risk |
| `AvailabilityChecker` stub always returns 50 — test must confirm behaviour | ChangeParticipantsDriverTest uses a mock/stub, not the real adapter |
| Controller test wiring (Spring Boot 4.x `@MockitoBean`) | Follow UC02/03 controller test pattern exactly |
