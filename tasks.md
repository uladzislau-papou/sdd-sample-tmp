# Tasks – UC04: ChangeParticipants

## Phase 1: Domain Event

### 1.1 – ParticipantsChanged Event
- [x] **Task 1.1**: Create `core/domain/event/ParticipantsChanged.java` as a `record` implementing `DomainEvent` with fields: `BookingId bookingId`, `ParticipantCount newParticipantCount`, `Instant occurredAt`.

---

## Phase 2: Domain Model

### 2.1 – TourBooking.changeParticipants()
- [x] **Task 2.1**: Remove `final` from the `participantCount` and `availableCapacity` fields in `TourBooking.java`.
- [x] **Task 2.2**: Add the `changeParticipants(ParticipantCount newCount, AvailableCapacity freshCapacity, Instant now)` method to `TourBooking.java`:
  - Guard: state must be `REQUESTED` or `CONFIRMED`, else throw `InvalidBookingStateException`.
  - Guard: `newCount.value() > freshCapacity.value()` → throw `CapacityExceededException`.
  - Re-assign `this.participantCount` and `this.availableCapacity`.
  - Append `new ParticipantsChanged(bookingId, newCount, now)` to `domainEvents`.
  - Add JavaDoc (following existing method style).

### 2.2 – Domain Tests
- [x] **Task 2.3**: Add to `TourBookingTest.java` — happy path: `changeParticipants` increases count successfully; assert `participantCount()` reflects new value and one `ParticipantsChanged` event is in `pullDomainEvents()`.
- [x] **Task 2.4**: Add to `TourBookingTest.java` — happy path: `changeParticipants` decreases count successfully.
- [x] **Task 2.5**: Add to `TourBookingTest.java` — invalid state: calling `changeParticipants` on a CANCELLED booking throws `InvalidBookingStateException`.
- [x] **Task 2.6**: Add to `TourBookingTest.java` — capacity exceeded: `newCount > freshCapacity` throws `CapacityExceededException`.
- [x] **Task 2.7**: Run `./gradlew clean test` — all domain tests must be green.

---

## Phase 3: Inport (Use Case Contract)

### 3.1 – Inport Types
- [x] **Task 3.1**: Create `core/inport/ChangeParticipantsCommand.java` as a `record` with fields: `String bookingId`, `int newParticipantCount`.
- [x] **Task 3.2**: Create `core/inport/ChangeParticipantsResult.java` as a `record` with field: `int participantCount`.
- [x] **Task 3.3**: Create `core/inport/ChangeParticipantsUseCase.java` as an interface with method `ChangeParticipantsResult change(ChangeParticipantsCommand command)`. Add JavaDoc referencing the UC04 spec.

---

## Phase 4: Application Service (Driver)

### 4.1 – ChangeParticipantsDriver
- [x] **Task 4.1**: Create `inbound/driver/ChangeParticipantsDriver.java` annotated `@Service @Transactional`, implementing `ChangeParticipantsUseCase`. Constructor-inject `TourBookingRepository`, `AvailabilityChecker`, `DomainEventPublisher`, `ClockPort`.
- [x] **Task 4.2**: Implement `change(command)`:
  1. Parse `bookingId` from `UUID.fromString(command.bookingId())`.
  2. Load aggregate via `repository.findById(bookingId)` → throw `BookingNotFoundException` if absent.
  3. Get `now` from `clockPort.now()`.
  4. Call `availabilityChecker.checkAvailability(booking.tourId(), booking.tourDate())`.
  5. Call `booking.changeParticipants(new ParticipantCount(command.newParticipantCount()), freshCapacity, now)`.
  6. Call `repository.update(booking)`.
  7. Call `booking.pullDomainEvents().forEach(domainEventPublisher::publish)`.
  8. Return `new ChangeParticipantsResult(booking.participantCount().value())`.

### 4.2 – Driver Tests
- [x] **Task 4.3**: Create `inbound/driver/ChangeParticipantsDriverTest.java` (no Spring, use Mockito). Set up stubs for all four ports.
- [x] **Task 4.4**: Test — happy path: stub returns booking in REQUESTED state with capacity 10, change to 5; assert result `participantCount == 5` and `repository.update()` was called.
- [x] **Task 4.5**: Test — booking not found: `repository.findById` returns empty → assert `BookingNotFoundException` is thrown.
- [x] **Task 4.6**: Test — invalid state: booking in CANCELLED state → assert `InvalidBookingStateException` is thrown.
- [x] **Task 4.7**: Test — capacity exceeded: `availabilityChecker` returns capacity 2, new count is 10 → assert `CapacityExceededException` is thrown.
- [x] **Task 4.8**: Test — availability unavailable: `availabilityChecker` throws `AvailabilityUnavailableException` → assert it propagates.
- [x] **Task 4.9**: Run `./gradlew clean test` — all driver tests must be green.

---

## Phase 5: REST Layer

### 5.1 – DTOs
- [x] **Task 5.1**: Create `inbound/rest/ChangeParticipantsRequest.java` as a `record` with field `@NotNull @Min(1) Integer newParticipantCount`.
- [x] **Task 5.2**: Create `inbound/rest/ChangeParticipantsResponse.java` as a `record` with field `int participantCount`.

### 5.2 – Controller Endpoint
- [x] **Task 5.3**: Add `ChangeParticipantsUseCase` as a constructor-injected field in `TourBookingController.java`.
- [x] **Task 5.4**: Add endpoint to `TourBookingController.java`:
  ```
  PATCH /{bookingId}/participants
  ```
  Map `ChangeParticipantsRequest` → `ChangeParticipantsCommand` → call use case → return `200 OK` with `ChangeParticipantsResponse`.

### 5.3 – Controller Tests
- [x] **Task 5.5**: Add to `TourBookingControllerTest.java` — 200 happy path: mock use case returns result; assert response body contains updated `participantCount`.
- [x] **Task 5.6**: Add to `TourBookingControllerTest.java` — 404: mock use case throws `BookingNotFoundException`; assert HTTP 404.
- [x] **Task 5.7**: Add to `TourBookingControllerTest.java` — 409 invalid state: mock throws `InvalidBookingStateException`; assert HTTP 409.
- [x] **Task 5.8**: Add to `TourBookingControllerTest.java` — 409 capacity exceeded: mock throws `CapacityExceededException`; assert HTTP 409.
- [x] **Task 5.9**: Run `./gradlew clean test` — all controller tests must be green.

---

## Phase 6: HTTP Client File & Verification

### 6.1 – HTTP Client File
- [x] **Task 6.1**: Create `rest/uc04-change-participants.http` with four requests:
  - Happy path: valid bookingId + `newParticipantCount` in body → expect 200.
  - 404 case: non-existent bookingId → expect 404.
  - 409 invalid state case: bookingId of a CANCELLED booking → expect 409.
  - 409 capacity exceeded case: `newParticipantCount` exceeding available capacity → expect 409.

### 6.2 – Final Verification
- [x] **Task 6.2**: Run `./gradlew clean test` — all tests green.
- [x] **Task 6.3**: Run `./gradlew build` — build succeeds with no warnings treated as errors.
