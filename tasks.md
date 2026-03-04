# Tasks – UC03 Cancel Tour Booking

---

## Phase 1: Domain Layer

### 1.1 – Domain Event `TourBookingCancelled`
- [x] **Task 1.1**: Create `core/domain/event/TourBookingCancelled.java` as a `record` implementing `DomainEvent` with fields `BookingId bookingId` and `Instant cancelledAt`.

### 1.2 – Domain Method `TourBooking.cancel()`
- [x] **Task 1.2**: Add `cancel(Instant now)` method to `TourBooking.java`.
  - Guard: throw `InvalidBookingStateException(status)` if status is neither `REQUESTED` nor `CONFIRMED`.
  - Transition: `status = CANCELLED`.
  - Record event: `domainEvents.add(new TourBookingCancelled(bookingId, now))`.
- [x] **Task 1.3**: Update the class-level Javadoc in `TourBooking.java` to mention `cancel`.

---

## Phase 2: Port Interfaces (Inports)

### 2.1 – Inport Types
- [x] **Task 2.1**: Create `core/inport/CancelTourBookingCommand.java` — `record(String bookingId)`.
- [x] **Task 2.2**: Create `core/inport/CancelTourBookingResult.java` — `record(String status)`.
- [x] **Task 2.3**: Create `core/inport/CancelTourBookingUseCase.java` — interface with `CancelTourBookingResult cancel(CancelTourBookingCommand command)`.

---

## Phase 3: Driver (Application Service)

### 3.1 – `CancelTourBookingDriver`
- [x] **Task 3.1**: Create `inbound/driver/CancelTourBookingDriver.java` annotated `@Service @Transactional` implementing `CancelTourBookingUseCase`.
  - Inject: `TourBookingRepository`, `DomainEventPublisher`, `ClockPort`.
  - Flow: parse `BookingId` → `clockPort.now()` → `findById` (throw `BookingNotFoundException` if empty) → `booking.cancel(now)` → `repository.update(booking)` → `pullDomainEvents().forEach(publisher::publish)` → return `CancelTourBookingResult(booking.status().name())`.

---

## Phase 4: REST Layer

### 4.1 – Response DTO
- [x] **Task 4.1**: Create `inbound/rest/CancelTourBookingResponse.java` — `record(String status)`.

### 4.2 – Controller Endpoint
- [x] **Task 4.2**: Inject `CancelTourBookingUseCase` into `TourBookingController` (constructor injection).
- [x] **Task 4.3**: Add `DELETE /{bookingId}` handler to `TourBookingController`:
  - Map path variable to `CancelTourBookingCommand`.
  - Return `200 OK` with `CancelTourBookingResponse`.
  - Update class-level SDD Javadoc to reference UC03.

---

## Phase 5: Tests

### 5.1 – Domain Tests
- [x] **Task 5.1**: Extend `TourBookingTest.java` with:
  - `cancel_fromRequested_transitionsToCancelled()`
  - `cancel_fromConfirmed_transitionsToCancelled()`
  - `cancel_fromActive_throwsInvalidBookingStateException()`
  - `cancel_fromCompleted_throwsInvalidBookingStateException()`
  - `cancel_fromCancelled_throwsInvalidBookingStateException()`
  - `cancel_publishesTourBookingCancelledEvent()`

### 5.2 – Driver Tests
- [x] **Task 5.2**: Create `inbound/driver/CancelTourBookingDriverTest.java` (no Spring, stub ports):
  - Happy path: REQUESTED booking → `result.status() == "CANCELLED"`, event published.
  - Happy path: CONFIRMED booking → same.
  - Not-found guard: empty repo → `BookingNotFoundException` thrown.
  - Invalid state guard: ACTIVE booking → `InvalidBookingStateException` propagated.
  - (Pre-load stubs via `booking.pullDomainEvents()` to clear pending events before inserting into stub repo.)

### 5.3 – Controller / Web Tests
- [x] **Task 5.3**: Add UC03 cases to `TourBookingControllerTest.java`:
  - `DELETE /api/v1/bookings/{id}` → 200 `{ "status": "CANCELLED" }`.
  - Unknown ID → 404 `{ "error": "…" }`.
  - Invalid state → 409 `{ "error": "…" }`.

### 5.4 – Persistence Integration Tests
- [x] **Task 5.4**: Extend `TourBookingJooqRepositoryIT.java`:
  - Save booking → `cancel(now)` → `update()` → `findById()` → assert `status == CANCELLED`.

---

## Phase 6: REST Client & Spec

### 6.1 – REST Client File
- [x] **Task 6.1**: Create `rest/uc03-cancel-tour-booking.http` covering:
  - Happy path: `DELETE /api/v1/bookings/{{bookingId}}`.
  - Error: booking not found → 404.
  - Error: invalid state → 409.

### 6.2 – Spec Update
- [x] **Task 6.2**: Update `documentation/use-cases/uc03-cancle-tour-booking.spec.md`:
  - Status → `IMPLEMENTED`.
  - Add valid source states (REQUESTED, CONFIRMED).
  - Add REST endpoint definition (`DELETE /api/v1/bookings/{bookingId}`).
  - Add success response (`200 OK`, `{ "status": "CANCELLED" }`).
  - Add full error table (404, 409).
  - Add detailed flow with class references.
  - Add detailed acceptance criteria.
  - Add failure scenarios table.
  - Add full test requirements.

---

## Phase 7: Verify

### 7.1 – Build & Test
- [x] **Task 7.1**: Run `./gradlew clean test` — all tests green.
- [x] **Task 7.2**: Run `./gradlew build` — build succeeds.
