# Tasks – Extract Guide Bounded Context

ADR: `documentation/adr/0003-separate-guide-bounded-context.adr.md`

---

## Phase 1: Move TourId to shared.domain

### 1.1 – Relocate TourId
- [x] **Task 1.1**: Create `shared/domain/TourId.java` — copy class body from `booking.core.domain.TourId`, update package declaration to `com.dominikgaller.alpinebooking.shared.domain`.
- [x] **Task 1.2**: Update import in `booking/core/domain/TourBooking.java` → `shared.domain.TourId`.
- [x] **Task 1.3**: Update import in `booking/core/domain/event/TourBookingRequested.java` → `shared.domain.TourId`.
- [x] **Task 1.4**: Update import in `booking/core/outport/AvailabilityChecker.java` → `shared.domain.TourId`.
- [x] **Task 1.5**: Update import in `booking/outbound/persistence/write/TourBookingMapper.java` → `shared.domain.TourId`.
- [x] **Task 1.6**: Update import in `booking/core/domain/GuideTour.java` → `shared.domain.TourId` (was same-package, now needs explicit import).
- [x] **Task 1.7**: Update import in `booking/core/domain/event/TourStarted.java` → `shared.domain.TourId`.
- [x] **Task 1.8**: Add import to `booking/core/domain/TourBooking.java` → `shared.domain.TourId` (was same-package, now needs explicit import).
- [x] **Task 1.9**: Update import in `booking/inbound/driver/RequestTourBookingDriver.java` → `shared.domain.TourId`.
- [x] **Task 1.10**: Update import in `booking/outbound/integration/StubAvailabilityChecker.java` → `shared.domain.TourId`.
- [x] **Task 1.11**: Update import in `booking/outbound/persistence/write/GuideTourMapper.java` → `shared.domain.TourId`.
- [x] **Task 1.12**: Update import in all `booking` test files that import `booking.core.domain.TourId` → `shared.domain.TourId` (driver tests + IT tests + domain tests).
- [x] **Task 1.13**: Delete old `booking/core/domain/TourId.java`.
- [x] **Task 1.14**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 2: Create guide Domain Layer

### 2.1 – Value Objects and Aggregate
- [x] **Task 2.1**: Create `guide/core/domain/GuideTourId.java` — re-package from `booking.core.domain.GuideTourId`.
- [x] **Task 2.2**: Create `guide/core/domain/GuideTourStatus.java` — re-package from `booking.core.domain.GuideTourStatus`.
- [x] **Task 2.3**: Create `guide/core/domain/event/TourStarted.java` — re-package from `booking.core.domain.event.TourStarted`; update `GuideTourId` and `TourId` imports.
- [x] **Task 2.4**: Create `guide/core/domain/exception/GuideTourNotFoundException.java` — re-package from `booking.core.domain.exception.GuideTourNotFoundException`.
- [x] **Task 2.5**: Create `guide/core/domain/exception/InvalidGuideTourStateException.java` — re-package from `booking.core.domain.exception.InvalidGuideTourStateException`; update `GuideTourStatus` import.
- [x] **Task 2.6**: Create `guide/core/domain/exception/TourStartTooEarlyException.java` — re-package from `booking.core.domain.exception.TourStartTooEarlyException`.
- [x] **Task 2.7**: Create `guide/core/domain/GuideTour.java` — re-package from `booking.core.domain.GuideTour`; update all imports to new `guide` packages.
- [x] **Task 2.8**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 3: Create guide Inport and Outport

### 3.1 – Ports
- [x] **Task 3.1**: Create `guide/core/outport/GuideTourRepository.java` — re-package from `booking.core.outport.GuideTourRepository`; update `GuideTourId` and `GuideTour` imports.
- [x] **Task 3.2**: Create `guide/core/inport/command/StartTourCommand.java` — re-package from `booking.core.inport.command.StartTourCommand`.
- [x] **Task 3.3**: Create `guide/core/inport/result/StartTourResult.java` — re-package from `booking.core.inport.result.StartTourResult`.
- [x] **Task 3.4**: Create `guide/core/inport/usecase/StartTourUseCase.java` — re-package from `booking.core.inport.usecase.StartTourUseCase`; update exception and command/result imports.
- [x] **Task 3.5**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 4: Create guide Driver

### 4.1 – Application Service
- [x] **Task 4.1**: Create `guide/inbound/driver/StartTourDriver.java` — re-package from `booking.inbound.driver.StartTourDriver`; update all imports to `guide.*`.
- [x] **Task 4.2**: Move `ClockPort` to `shared/outport/ClockPort.java` — cross-context port needed by both `booking` and `guide`.
- [x] **Task 4.3**: Move `DomainEventPublisher` to `shared/outport/DomainEventPublisher.java` — cross-context port needed by both contexts.
- [x] **Task 4.4**: Update `booking/core/outport/ClockPort.java` and `booking/core/outport/DomainEventPublisher.java` — delete old files after updating all imports in `booking.*`.
- [x] **Task 4.5**: Update `guide/inbound/driver/StartTourDriver.java` — replace `booking.core.outport` imports with `shared.outport` imports.
- [x] **Task 4.6**: Update implementations: `SystemClockPort` and `LoggingDomainEventPublisher` → import `shared.outport.*`.
- [x] **Task 4.7**: Update all `booking` drivers and tests that import `ClockPort` or `DomainEventPublisher` → `shared.outport.*`.
- [x] **Task 4.8**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 5: Create guide REST Layer

### 5.1 – DTOs and API Contract
- [x] **Task 5.1**: Create `guide/inbound/rest/request/StartTourRequest.java` — re-package from `booking.inbound.rest.request.StartTourRequest`.
- [x] **Task 5.2**: Create `guide/inbound/rest/response/StartTourResponse.java` — re-package from `booking.inbound.rest.response.StartTourResponse`.
- [x] **Task 5.3**: Create `guide/inbound/rest/GuideTourRestAPI.java` — re-package from `booking.inbound.rest.GuideTourRestAPI`; update DTO imports.
- [x] **Task 5.4**: Create `guide/inbound/rest/GuideTourController.java` — re-package from `booking.inbound.rest.GuideTourController`; update all imports to `guide.*`.

### 5.2 – Exception Handler
- [x] **Task 5.5**: Create `guide/inbound/rest/GuideExceptionHandler.java` — new `@RestControllerAdvice` handling `GuideTourNotFoundException` (404), `InvalidGuideTourStateException` (409), `TourStartTooEarlyException` (409); mirror the `ErrorResponse` record pattern from `BookingExceptionHandler`.
- [x] **Task 5.6**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 6: Create guide Persistence Layer

### 6.1 – Repository Adapter
- [x] **Task 6.1**: Create `guide/outbound/persistence/write/GuideTourMapper.java` — re-package from `booking.outbound.persistence.write.GuideTourMapper`; update `GuideTour`, `GuideTourId`, `GuideTourStatus`, `TourId` imports.
- [x] **Task 6.2**: Create `guide/outbound/persistence/write/GuideTourJooqRepository.java` — re-package from `booking.outbound.persistence.write.GuideTourJooqRepository`; update `GuideTour`, `GuideTourId`, `GuideTourRepository`, `GuideTourMapper` imports.
- [x] **Task 6.3**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 7: Bootstrap Wiring

### 7.1 – GuideConfig
- [x] **Task 7.1**: Create `bootstrap/GuideConfig.java` — `@Configuration` class; no new beans needed (ClockPort and DomainEventPublisher are already declared in `BookingConfig`); add a Javadoc noting the context and referencing ADR 0003.
- [x] **Task 7.2**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 8: Migrate Tests

### 8.1 – Domain Tests
- [x] **Task 8.1**: Create `guide/core/domain/GuideTourTest.java` — re-package from `booking.core.domain.GuideTourTest`; update all imports to `guide.*` and `shared.domain.TourId`.

### 8.2 – Controller Tests
- [x] **Task 8.2**: Create `guide/inbound/rest/GuideTourControllerTest.java` — re-package from `booking.inbound.rest.GuideTourControllerTest`; update all imports to `guide.*`.

### 8.3 – Persistence Integration Tests
- [x] **Task 8.3**: Create `guide/outbound/persistence/write/GuideTourJooqRepositoryIT.java` — re-package from `booking.outbound.persistence.write.GuideTourJooqRepositoryIT`; update all imports to `guide.*`.
- [x] **Task 8.4**: Run `./gradlew test` — all tests (including new guide tests) must pass.

---

## Phase 9: Clean up booking Context

### 9.1 – Remove old GuideTour files from booking
- [x] **Task 9.1**: Delete `booking/core/domain/GuideTour.java`.
- [x] **Task 9.2**: Delete `booking/core/domain/GuideTourId.java`.
- [x] **Task 9.3**: Delete `booking/core/domain/GuideTourStatus.java`.
- [x] **Task 9.4**: Delete `booking/core/domain/event/TourStarted.java`.
- [x] **Task 9.5**: Delete `booking/core/domain/exception/GuideTourNotFoundException.java`.
- [x] **Task 9.6**: Delete `booking/core/domain/exception/InvalidGuideTourStateException.java`.
- [x] **Task 9.7**: Delete `booking/core/domain/exception/TourStartTooEarlyException.java`.
- [x] **Task 9.8**: Delete `booking/core/outport/GuideTourRepository.java`.
- [x] **Task 9.9**: Delete `booking/core/inport/command/StartTourCommand.java`.
- [x] **Task 9.10**: Delete `booking/core/inport/result/StartTourResult.java`.
- [x] **Task 9.11**: Delete `booking/core/inport/usecase/StartTourUseCase.java`.
- [x] **Task 9.12**: Delete `booking/inbound/driver/StartTourDriver.java`.
- [x] **Task 9.13**: Delete `booking/inbound/rest/GuideTourController.java`.
- [x] **Task 9.14**: Delete `booking/inbound/rest/GuideTourRestAPI.java`.
- [x] **Task 9.15**: Delete `booking/inbound/rest/request/StartTourRequest.java`.
- [x] **Task 9.16**: Delete `booking/inbound/rest/response/StartTourResponse.java`.
- [x] **Task 9.17**: Delete `booking/outbound/persistence/write/GuideTourJooqRepository.java`.
- [x] **Task 9.18**: Delete `booking/outbound/persistence/write/GuideTourMapper.java`.
- [x] **Task 9.19**: Delete old test `booking/core/domain/GuideTourTest.java`.
- [x] **Task 9.20**: Delete old test `booking/inbound/rest/GuideTourControllerTest.java`.
- [x] **Task 9.21**: Delete old test `booking/outbound/persistence/write/GuideTourJooqRepositoryIT.java`.

### 9.2 – Update BookingExceptionHandler
- [x] **Task 9.22**: Remove `GuideTourNotFoundException`, `InvalidGuideTourStateException`, and `TourStartTooEarlyException` handlers from `BookingExceptionHandler`; remove their imports; update Javadoc.
- [x] **Task 9.23**: Run `./gradlew compileJava` — must compile clean.

---

## Phase 10: Final Verification

### 10.1 – Full Build
- [x] **Task 10.1**: Run `./gradlew clean test` — full test suite green.
- [x] **Task 10.2**: Run `./gradlew build` — build succeeds.
- [x] **Task 10.3**: Verify no `booking.*` file imports from `guide.*` and vice versa (grep check).
