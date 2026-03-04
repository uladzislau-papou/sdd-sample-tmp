# Tasks: UC02 – Confirm Tour Booking

---

## Phase 1: Spec Enrichment

### 1.1 – UC02 Spec Update

- [x] **Task 1.1**: Open `documentation/use-cases/uc02-confirm-tour-booking.spec.md` and update the **Status** field from `SPECIFIED` to `IN PROGRESS`.
- [x] **Task 1.2**: Add the REST endpoint definition to the spec: method `POST`, path `/api/v1/bookings/{bookingId}/confirm`, success `200 OK`, response `{ "status": "CONFIRMED" }`.
- [x] **Task 1.3**: Annotate each error type in the spec with its HTTP status code: `NotFound → 404 Not Found`, `InvalidState → 409 Conflict`, both returning `{ "error": "<message>" }`.
- [x] **Task 1.4**: Add the concrete exception class names to the spec's Failure Scenarios: `BookingNotFoundException` (404), `InvalidBookingStateException` (409).
- [x] **Task 1.5**: Add the bookingId validation rule to the Input Contract: UUID format, required, provided as path variable.

---

## Phase 2: Domain Layer – New Types

### 2.1 – DomainEvent Marker Interface

- [x] **Task 2.1**: Create `core/domain/event/DomainEvent.java` — a package `com.dominikgaller.alpinebooking.booking.core.domain.event` marker interface with no methods.
- [x] **Task 2.2**: Update `core/domain/event/TourBookingRequested.java` — add `implements DomainEvent` to the record declaration.

### 2.2 – TourBookingConfirmed Event

- [x] **Task 2.3**: Create `core/domain/event/TourBookingConfirmed.java` — a record with fields `BookingId bookingId` and `Instant occurredAt`, implementing `DomainEvent`.

### 2.3 – New Domain Exceptions

- [x] **Task 2.4**: Create `core/domain/exception/InvalidBookingStateException.java` — extends `RuntimeException`, constructor takes `TourBookingStatus currentStatus`, message: `"Cannot confirm booking in state: <status>"`.
- [x] **Task 2.5**: Create `core/domain/exception/BookingNotFoundException.java` — extends `RuntimeException`, constructor takes `String bookingId`, message: `"Booking not found: <id>"`.

---

## Phase 3: Domain Layer – Aggregate Changes

### 3.1 – TourBooking Generalization & New Methods

- [x] **Task 3.1**: In `TourBooking.java`, change the `domainEvents` field type from `List<TourBookingRequested>` to `List<DomainEvent>` (update import accordingly).
- [x] **Task 3.2**: In `TourBooking.java`, change `pullDomainEvents()` return type from `List<TourBookingRequested>` to `List<DomainEvent>` (update Javadoc).
- [x] **Task 3.3**: In `TourBooking.java`, add the `reconstitute()` package-private static factory that accepts all seven fields (`BookingId, TourId, TourDate, ParticipantCount, AvailableCapacity, ParticipantContact, TourBookingStatus`) and directly calls the private constructor — no invariant checks.
- [x] **Task 3.4**: In `TourBooking.java`, add the `confirm()` method: if `status != REQUESTED` throw `InvalidBookingStateException(status)`; else set `status = CONFIRMED` and add a `TourBookingConfirmed` to `domainEvents`. Note: method signature is `confirm(Instant now)` to timestamp the event.

---

## Phase 4: Port Layer

### 4.1 – DomainEventPublisher Generalization

- [x] **Task 4.1**: In `core/outport/DomainEventPublisher.java`, change the `publish` method signature from `void publish(TourBookingRequested event)` to `void publish(DomainEvent event)` (update import to `DomainEvent`).

### 4.2 – TourBookingRepository Extension

- [x] **Task 4.2**: In `core/outport/TourBookingRepository.java`, add method `Optional<TourBooking> findById(BookingId bookingId)` (add `java.util.Optional` import).
- [x] **Task 4.3**: In `core/outport/TourBookingRepository.java`, add method `void update(TourBooking booking)`.

### 4.3 – Confirm Inports

- [x] **Task 4.4**: Create `core/inport/ConfirmTourBookingCommand.java` — record with single field `String bookingId`.
- [x] **Task 4.5**: Create `core/inport/ConfirmTourBookingResult.java` — record with single field `String status`.
- [x] **Task 4.6**: Create `core/inport/ConfirmTourBookingUseCase.java` — interface with method `ConfirmTourBookingResult confirm(ConfirmTourBookingCommand command)`.

---

## Phase 5: Persistence Adapter

### 5.1 – TourBookingMapper: Reverse Mapping

- [x] **Task 5.1**: In `outbound/persistence/write/TourBookingMapper.java`, add method `TourBooking toDomain(TourBookingRecord record)` that calls `TourBooking.reconstitute(...)` mapping each column to its corresponding value object (`BookingId`, `TourId`, `TourDate`, `ParticipantCount`, `AvailableCapacity`, `ParticipantContact`, `TourBookingStatus.valueOf(record.getStatus())`).

### 5.2 – TourBookingJooqRepository: findById & update

- [x] **Task 5.2**: In `TourBookingJooqRepository.java`, implement `findById(BookingId bookingId)`: SELECT from `TOUR_BOOKING` where `ID = bookingId.value().toString()`, return `Optional.empty()` if not found, else `Optional.of(mapper.toDomain(record))`.
- [x] **Task 5.3**: In `TourBookingJooqRepository.java`, implement `update(TourBooking booking)`: execute `UPDATE tour_booking SET status = ? WHERE id = ?`; assert the returned row count is exactly 1 (throw `IllegalStateException` if not).

---

## Phase 6: Application Driver

### 6.1 – ConfirmTourBookingDriver

- [x] **Task 6.1**: Create `inbound/driver/ConfirmTourBookingDriver.java` — `@Service @Transactional`, implementing `ConfirmTourBookingUseCase`.
- [x] **Task 6.2**: Inject `TourBookingRepository`, `DomainEventPublisher`, and `ClockPort` via constructor. (`ClockPort` added: `confirm(Instant now)` requires a timestamp for `TourBookingConfirmed`.)
- [x] **Task 6.3**: Implement `confirm(ConfirmTourBookingCommand command)`:
  1. `new BookingId(UUID.fromString(command.bookingId()))`
  2. `clockPort.now()` to get current timestamp
  3. `tourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
  4. `booking.confirm(now)` (throws `InvalidBookingStateException` on wrong state)
  5. `tourBookingRepository.update(booking)`
  6. `booking.pullDomainEvents().forEach(domainEventPublisher::publish)`
  7. Return `new ConfirmTourBookingResult(booking.status().name())`

---

## Phase 7: REST Layer

### 7.1 – Response DTO

- [x] **Task 7.1**: Create `inbound/rest/ConfirmTourBookingResponse.java` — record with single field `String status`.

### 7.2 – Controller Endpoint

- [x] **Task 7.2**: In `TourBookingController.java`, inject `ConfirmTourBookingUseCase` via constructor (add field and constructor parameter).
- [x] **Task 7.3**: In `TourBookingController.java`, add `@PostMapping("/{bookingId}/confirm")` method that calls `confirmTourBookingUseCase.confirm(new ConfirmTourBookingCommand(bookingId))` and returns `ResponseEntity.ok(new ConfirmTourBookingResponse(result.status()))`.

### 7.3 – Exception Handlers

- [x] **Task 7.4**: In `BookingExceptionHandler.java`, add handler for `BookingNotFoundException` annotated with `@ResponseStatus(HttpStatus.NOT_FOUND)`, returning `new ErrorResponse(ex.getMessage())`.
- [x] **Task 7.5**: In `BookingExceptionHandler.java`, add handler for `InvalidBookingStateException` annotated with `@ResponseStatus(HttpStatus.CONFLICT)`, returning `new ErrorResponse(ex.getMessage())`.

### 7.4 – Fix Compilation: LoggingDomainEventPublisher

- [x] **Task 7.6**: In `outbound/integration/LoggingDomainEventPublisher.java`, change the `publish` method parameter from `TourBookingRequested event` to `DomainEvent event` (update import; `applicationEventPublisher.publishEvent(event)` call is unchanged).

### 7.5 – REST Client File

- [x] **Task 7.7**: Create `rest/uc02-confirm-tour-booking.http`:
  - Happy path: `POST http://localhost:8080/api/v1/bookings/{{bookingId}}/confirm` (no body), expect `200 OK { "status": "CONFIRMED" }`.
  - Error 404: same URL with an unknown UUID, expect `404 Not Found`.
  - Error 409: comment explaining to call UC01 first, then call confirm on a booking that is already CONFIRMED, expect `409 Conflict`.

---

## Phase 8: Tests

### 8.1 – Domain Tests: TourBookingTest

- [x] **Task 8.1**: In `TourBookingTest.java`, add test `confirm_transitionsStatusToConfirmed`: create a REQUESTED booking via `request()`, call `confirm()`, assert `status() == CONFIRMED`.
- [x] **Task 8.2**: In `TourBookingTest.java`, add test `confirm_publishesTourBookingConfirmedEvent`: after `confirm()`, call `pullDomainEvents()`, assert list has size 1 and the event is a `TourBookingConfirmed` with the correct `bookingId`.
- [x] **Task 8.3**: In `TourBookingTest.java`, add test `confirm_throwsInvalidBookingStateException_whenNotRequested`: create a REQUESTED booking, call `confirm()` once, then call `confirm()` again and assert `assertThatThrownBy(...).isInstanceOf(InvalidBookingStateException.class)`.
- [x] **Task 8.4**: In `TourBookingTest.java`, add test `pullDomainEvents_returnsEmptyList_afterPullingTwice`: verify that after pulling events from `request()`, calling `confirm()`, then pulling events again — only `TourBookingConfirmed` is returned (not `TourBookingRequested` again). Note: also fixed existing test's `List<TourBookingRequested>` type to `List<DomainEvent>` (compile fix from Phase 3).

### 8.2 – Driver Tests: ConfirmTourBookingDriverTest

- [x] **Task 8.5**: Create `inbound/driver/ConfirmTourBookingDriverTest.java` with inline stub/mock infrastructure (no Spring — same pattern as `RequestTourBookingDriverTest`).
- [x] **Task 8.6**: Add test `confirm_happyPath_returnsConfirmedStatus`: stub `findById` to return a REQUESTED booking, call driver, assert result status is `"CONFIRMED"`.
- [x] **Task 8.7**: Add test `confirm_happyPath_callsUpdateOnRepository`: verify `update()` is called exactly once with a booking in CONFIRMED status.
- [x] **Task 8.8**: Add test `confirm_happyPath_publishesTourBookingConfirmedEvent`: verify `publish()` is called exactly once with a `TourBookingConfirmed` event.
- [x] **Task 8.9**: Add test `confirm_throwsBookingNotFoundException_whenNotFound`: stub `findById` to return `Optional.empty()`, assert `assertThatThrownBy(...).isInstanceOf(BookingNotFoundException.class)`.
- [x] **Task 8.10**: Add test `confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed`: pre-confirm the booking in the stub repo (via `booking.confirm(now)`), assert `assertThatThrownBy(...).isInstanceOf(InvalidBookingStateException.class)`. Note: `reconstitute()` is package-private so used `request()` + `confirm()` instead.
- [x] **Task 8.11**: Add test `confirm_doesNotCallUpdate_whenStateInvalid`: verify `update()` is never called when `InvalidBookingStateException` is thrown.

### 8.3 – Persistence Integration Tests: TourBookingJooqRepositoryIT

- [x] **Task 8.12**: Add test `findById_returnsEmpty_whenNotFound`: call `findById` with a random UUID, assert `Optional.isEmpty()`.
- [x] **Task 8.13**: Add test `findById_returnsAggregate_afterSave`: `save` a booking, then `findById` with the same ID, assert all fields match (bookingId, tourId, tourDate, participantCount, status).
- [x] **Task 8.14**: Add test `update_changesStatus_inDatabase`: `save` a REQUESTED booking, call `booking.confirm()`, call `update(booking)`, then `findById`, assert status is `CONFIRMED`.

### 8.4 – REST Tests: TourBookingControllerTest

- [x] **Task 8.15**: Add test `confirmBooking_returns200_withConfirmedStatus`: mock `ConfirmTourBookingUseCase` to return `new ConfirmTourBookingResult("CONFIRMED")`, perform `POST /api/v1/bookings/{uuid}/confirm`, assert `200 OK` and JSON `status == "CONFIRMED"`.
- [x] **Task 8.16**: Add test `confirmBooking_returns404_whenNotFound`: mock use case to throw `BookingNotFoundException`, assert `404 Not Found` with JSON `error` field.
- [x] **Task 8.17**: Add test `confirmBooking_returns409_whenInvalidState`: mock use case to throw `InvalidBookingStateException`, assert `409 Conflict` with JSON `error` field. Note: also added `@MockitoBean ConfirmTourBookingUseCase confirmUseCase` field to cover the controller's new constructor dependency.

---

## Phase 9: Verify & Closeout

### 9.1 – Build Verification

- [x] **Task 9.1**: Run `./gradlew clean test` — 53 tests, 0 failures. Fix applied: `TourBooking.reconstitute()` made `public` (was package-private; `TourBookingMapper` is in a different package). Also fixed `ConfirmTourBookingDriverTest.requestedBooking()` to drain initial `TourBookingRequested` event (simulates DB reconstitution).
- [x] **Task 9.2**: Run `./gradlew build` — BUILD SUCCESSFUL.

### 9.2 – Spec Closeout

- [x] **Task 9.3**: Update `documentation/use-cases/uc02-confirm-tour-booking.spec.md` — set **Status** to `IMPLEMENTED`.
- [x] **Task 9.4**: Update `plan.md` — set **Status** to `DONE`.