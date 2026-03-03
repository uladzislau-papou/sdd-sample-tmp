# Tasks – UC01: RequestTourBooking

---

## Phase 1: Specification & ADR

### 1.1 – Port Spec: RequestTourBookingUseCase (Inport)
- [x] **Task 1.1.1**: Create `documentation/ports/request-tour-booking.inport.spec.md` with method contract `RequestTourBookingResult request(RequestTourBookingCommand)`, command fields, result fields, exceptions, and transactional note.

### 1.2 – Port Spec: TourBookingRepository (Outport)
- [x] **Task 1.2.1**: Create `documentation/ports/tour-booking-repository.outport.spec.md` with method contract `void save(TourBooking)`, atomicity guarantee, and note that no update is needed for UC01.

### 1.3 – Port Spec: AvailabilityChecker (Outport)
- [x] **Task 1.3.1**: Create `documentation/ports/availability-checker.outport.spec.md` with method contract `AvailableCapacity checkAvailability(TourId, TourDate)`, `AvailabilityUnavailableException` contract, and note that the aggregate (not this port) enforces the capacity invariant.

### 1.4 – Port Spec: DomainEventPublisher (Outport)
- [x] **Task 1.4.1**: Create `documentation/ports/domain-event-publisher.outport.spec.md` with method contract `void publish(TourBookingRequested)`, reference to ADR 0002 for post-commit strategy, and reference implementation note.

### 1.5 – Port Spec: ClockPort (Outport)
- [x] **Task 1.5.1**: Create `documentation/ports/clock.outport.spec.md` with method contract `Instant now()` and reference implementation note (`SystemClockPort` → `Instant.now()`).

### 1.6 – ADR 0002: Domain Event Publication Strategy
- [x] **Task 1.6.1**: Create `documentation/adr/0002-domain-event-publication.adr.md` with status Accepted, context (post-commit requirement from `modelling.definition.md`), decision (driver calls `DomainEventPublisher` inside `@Transactional`; implementation uses Spring `ApplicationEventPublisher`; `@TransactionalEventListener(phase = AFTER_COMMIT)` listener performs the side-effect), rationale, and consequences.

### 1.7 – Update Existing Specs
- [x] **Task 1.7.1**: Update `documentation/domain/aggregate-tour-booking.spec.md` – added `ParticipantContact`, `AvailableCapacity`, and all other value object definitions; refined `request()` method signature and preconditions.
- [x] **Task 1.7.2**: Update `documentation/use-cases/uc01-request-tour-booking.spec.md` – added REST contract section (`POST /api/v1/bookings`), expanded input fields with `contactName`/`contactEmail`, full error table, all acceptance criteria, updated flow to 6 steps.

---

## Phase 2: Build Setup

### 2.1 – Version Catalog
- [ ] **Task 2.1.1**: Replace `gradle/libs.versions.toml` – add versions and library entries for: Spring Boot 4.x, jOOQ (Java 21 compatible), Flyway, H2, AssertJ. Remove the Guava entry (no longer needed).

### 2.2 – Gradle Build Script
- [ ] **Task 2.2.1**: Replace `app/build.gradle.kts` – apply Spring Boot plugin and Spring Dependency Management plugin; add dependencies: `spring-boot-starter-web`, `spring-boot-starter-jooq`, `spring-boot-starter-test`, `flyway-core`, `h2`, `assertj-core`; set Java toolchain to 21; remove `application` plugin and `mainClass` setting.
- [ ] **Task 2.2.2**: Configure jOOQ code generation in `app/build.gradle.kts` – run H2 in-process, apply Flyway migrations, generate jOOQ classes into `app/build/generated-src/jooq/main`; make `compileJava` depend on the generation task.

### 2.3 – Application Configuration
- [ ] **Task 2.3.1**: Create `app/src/main/resources/application.yml` – H2 datasource (file mode for local dev), Flyway enabled, jOOQ SQL dialect H2.
- [ ] **Task 2.3.2**: Create `app/src/test/resources/application-test.yml` – H2 datasource (in-memory mode), Flyway enabled, same jOOQ dialect.

### 2.4 – Remove Placeholder Code
- [ ] **Task 2.4.1**: Delete `app/src/main/java/org/example/App.java`.
- [ ] **Task 2.4.2**: Delete `app/src/test/java/org/example/AppTest.java`.

### 2.5 – Build Verification
- [ ] **Task 2.5.1**: Run `./gradlew clean build` – verify the project compiles and the jOOQ generation task runs without errors (no domain code yet).

---

## Phase 3: Domain Layer

### 3.1 – Value Objects: IDs
- [ ] **Task 3.1.1**: Create `BookingId.java` in `core.domain` – record wrapping `UUID`; canonical constructor validates non-null; static factory `BookingId.of(UUID)` and `BookingId.generate()`.
- [ ] **Task 3.1.2**: Create `TourId.java` in `core.domain` – record wrapping `String`; canonical constructor validates non-blank.

### 3.2 – Value Objects: Booking Data
- [ ] **Task 3.2.1**: Create `TourDate.java` in `core.domain` – record wrapping `LocalDate`; canonical constructor validates non-null; method `boolean isInFuture(Instant now)` (compares date to now using UTC).
- [ ] **Task 3.2.2**: Create `ParticipantCount.java` in `core.domain` – record wrapping `int`; canonical constructor enforces value >= 1; throws `InvalidBookingRequestException`.
- [ ] **Task 3.2.3**: Create `AvailableCapacity.java` in `core.domain` – record wrapping `int`; canonical constructor enforces value >= 0.
- [ ] **Task 3.2.4**: Create `ParticipantContact.java` in `core.domain` – record with fields `name` (String) and `email` (String); canonical constructor validates both non-blank.

### 3.3 – Status Enum & Domain Exceptions
- [ ] **Task 3.3.1**: Create `TourBookingStatus.java` in `core.domain` – enum with values: `REQUESTED`, `CONFIRMED`, `CANCELLED`, `ACTIVE`, `COMPLETED`.
- [ ] **Task 3.3.2**: Create `exception/CapacityExceededException.java` in `core.domain` – unchecked exception; message includes requested count and available capacity.
- [ ] **Task 3.3.3**: Create `exception/InvalidBookingRequestException.java` in `core.domain` – unchecked exception; used for structural/temporal validation failures.

### 3.4 – Domain Event
- [ ] **Task 3.4.1**: Create `event/TourBookingRequested.java` in `core.domain` – immutable record with fields: `bookingId`, `tourId`, `tourDate`, `participantCount`, `occurredAt` (`Instant`).

### 3.5 – TourBooking Aggregate
- [ ] **Task 3.5.1**: Create `TourBooking.java` in `core.domain` – aggregate root class; private fields: `bookingId`, `tourId`, `tourDate`, `participantCount`, `availableCapacity`, `contact`, `status`; private mutable list of `domainEvents`; static factory `TourBooking.request(BookingId, TourId, TourDate, ParticipantCount, AvailableCapacity, Instant now)` enforcing all invariants (future date, capacity), setting `status = REQUESTED`, recording `TourBookingRequested`; public `List<TourBookingRequested> pullDomainEvents()` clears and returns recorded events; getters for all fields (no setters).

### 3.6 – Domain Tests: Value Objects
- [ ] **Task 3.6.1**: Create `BookingIdTest.java` – verify construction with valid UUID; verify `generate()` returns non-null; verify equality by value.
- [ ] **Task 3.6.2**: Create `TourDateTest.java` – verify `isInFuture` returns true for tomorrow; verify `isInFuture` returns false for yesterday; verify null input throws.
- [ ] **Task 3.6.3**: Create `ParticipantCountTest.java` – verify value 1 is valid; verify value 0 throws `InvalidBookingRequestException`; verify negative throws.
- [ ] **Task 3.6.4**: Create `ParticipantContactTest.java` – verify valid name+email; verify blank name throws; verify blank email throws.

### 3.7 – Domain Tests: TourBooking Aggregate
- [ ] **Task 3.7.1**: Create `TourBookingTest.java` – test: `request()` with valid input sets status `REQUESTED`; test: all field values are stored correctly; test: `pullDomainEvents()` returns exactly one `TourBookingRequested`; test: `pullDomainEvents()` called twice returns empty list on second call; test: past `tourDate` throws `InvalidBookingRequestException`; test: `participantCount > availableCapacity` throws `CapacityExceededException`; test: `participantCount == availableCapacity` is valid (boundary).

### 3.8 – Build Verification
- [ ] **Task 3.8.1**: Run `./gradlew clean test` – all domain tests green, no Spring context required.

---

## Phase 4: Port Interfaces

### 4.1 – Inport: RequestTourBookingUseCase
- [ ] **Task 4.1.1**: Create `RequestTourBookingCommand.java` in `core.inport` – record with fields: `tourId` (String), `tourDate` (LocalDate), `participantCount` (int), `contactName` (String), `contactEmail` (String).
- [ ] **Task 4.1.2**: Create `RequestTourBookingResult.java` in `core.inport` – record with fields: `bookingId` (String), `status` (String).
- [ ] **Task 4.1.3**: Create `RequestTourBookingUseCase.java` in `core.inport` – interface with single method `RequestTourBookingResult request(RequestTourBookingCommand command)`; add JavaDoc referencing `documentation/ports/request-tour-booking.inport.md`.

### 4.2 – Outports
- [ ] **Task 4.2.1**: Create `TourBookingRepository.java` in `core.outport` – interface with `void save(TourBooking booking)`; add JavaDoc.
- [ ] **Task 4.2.2**: Create `AvailabilityChecker.java` in `core.outport` – interface with `AvailableCapacity checkAvailability(TourId tourId, TourDate tourDate)`; declares `AvailabilityUnavailableException` (create as unchecked exception in `core.outport` or `core.domain`); add JavaDoc.
- [ ] **Task 4.2.3**: Create `DomainEventPublisher.java` in `core.outport` – interface with `void publish(TourBookingRequested event)`; add JavaDoc referencing ADR 0002.
- [ ] **Task 4.2.4**: Create `ClockPort.java` in `core.outport` – interface with `Instant now()`; add JavaDoc.

---

## Phase 5: Application Service (Driver)

### 5.1 – RequestTourBookingDriver
- [ ] **Task 5.1.1**: Create `RequestTourBookingDriver.java` in `inbound.driver` – annotate `@Service`, `@Transactional`; constructor-inject `TourBookingRepository`, `AvailabilityChecker`, `DomainEventPublisher`, `ClockPort`; implement `request(RequestTourBookingCommand)` with the full orchestration flow: map command to domain types → generate `BookingId` → call `clockPort.now()` → call `availabilityChecker.checkAvailability(...)` → call `TourBooking.request(...)` → call `tourBookingRepository.save(...)` → call `domainEventPublisher.publish(...)` for each pulled domain event → return `RequestTourBookingResult`.

### 5.2 – Use Case Tests
- [ ] **Task 5.2.1**: Create `RequestTourBookingDriverTest.java` in test scope – Spring-free; implement stub `TourBookingRepository` (records saved booking), stub `AvailabilityChecker` (returns fixed capacity), stub `DomainEventPublisher` (records published events), stub `ClockPort` (returns fixed future-safe `Instant`).
- [ ] **Task 5.2.2**: Add test: happy path – `request()` returns result with non-null `bookingId` and status `"REQUESTED"`; repository stub received exactly one save call; publisher stub received exactly one `TourBookingRequested`.
- [ ] **Task 5.2.3**: Add test: capacity exceeded – stub availability checker returns capacity 0; assert `CapacityExceededException` is thrown; assert repository save was NOT called.
- [ ] **Task 5.2.4**: Add test: availability infrastructure failure – stub checker throws `AvailabilityUnavailableException`; assert exception propagates; assert repository save was NOT called.

### 5.3 – Build Verification
- [ ] **Task 5.3.1**: Run `./gradlew clean test` – all domain and use case tests green (no persistence or REST context needed yet).

---

## Phase 6: Persistence Adapter

### 6.1 – Flyway Migration
- [ ] **Task 6.1.1**: Create `app/src/main/resources/db/migration/V1__DDL_create_tour_booking.sql` – `CREATE TABLE tour_booking` with columns: `id VARCHAR(36) PK`, `tour_id VARCHAR(255)`, `tour_date DATE`, `participant_count INT`, `available_capacity INT`, `contact_name VARCHAR(255)`, `contact_email VARCHAR(255)`, `status VARCHAR(50)`; all columns `NOT NULL`.

### 6.2 – jOOQ Code Generation
- [ ] **Task 6.2.1**: Run `./gradlew generateJooq` (or equivalent task) – verify jOOQ generates `TourBookingRecord` and related classes under `app/build/generated-src/jooq/main`.

### 6.3 – Repository Adapter
- [ ] **Task 6.3.1**: Create `TourBookingMapper.java` in `outbound.persistence.write` – pure mapping class; method `TourBookingRecord toRecord(TourBooking booking)` maps all domain fields to the jOOQ record; no Spring dependency.
- [ ] **Task 6.3.2**: Create `TourBookingJooqRepository.java` in `outbound.persistence.write` – annotate `@Repository`; constructor-inject jOOQ `DSLContext`; implement `save(TourBooking)` using `TourBookingMapper` to convert to record, then `dsl.insertInto(...).set(record).execute()`.

### 6.4 – Persistence Integration Tests
- [ ] **Task 6.4.1**: Create `TourBookingJooqRepositoryIT.java` in test scope – use `@SpringBootTest` (or a custom test slice); ensure Flyway migrations run; inject `TourBookingJooqRepository` and `DSLContext`.
- [ ] **Task 6.4.2**: Add test: `save` persists all fields – after `save`, query via `DSLContext`, assert every column matches the domain object values.
- [ ] **Task 6.4.3**: Add test: `save` a second booking with the same `id` – assert a constraint violation or duplication exception is thrown (PK uniqueness).

### 6.5 – Build Verification
- [ ] **Task 6.5.1**: Run `./gradlew clean test` – all domain, use case, and persistence integration tests green.

---

## Phase 7: REST Adapter, Stubs & Bootstrap

### 7.1 – REST DTOs
- [ ] **Task 7.1.1**: Create `RequestTourBookingRequest.java` in `inbound.rest` – record with fields: `tourId` (String), `tourDate` (LocalDate), `participantCount` (int), `contactName` (String), `contactEmail` (String); add `@NotBlank` / `@NotNull` / `@Min(1)` Bean Validation annotations.
- [ ] **Task 7.1.2**: Create `RequestTourBookingResponse.java` in `inbound.rest` – record with fields: `bookingId` (String), `status` (String).

### 7.2 – REST Controller
- [ ] **Task 7.2.1**: Create `TourBookingController.java` in `inbound.rest` – annotate `@RestController`, `@RequestMapping("/api/v1/bookings")`; constructor-inject `RequestTourBookingUseCase`; implement `POST /` method: validate `@RequestBody @Valid RequestTourBookingRequest`, map to `RequestTourBookingCommand`, call use case, return `ResponseEntity<RequestTourBookingResponse>` with HTTP 201.

### 7.3 – Exception Handler
- [ ] **Task 7.3.1**: Create `BookingExceptionHandler.java` in `inbound.rest` – annotate `@RestControllerAdvice`; handle `InvalidBookingRequestException` → HTTP 400; handle `CapacityExceededException` → HTTP 409; handle `AvailabilityUnavailableException` → HTTP 502; each handler returns a simple error body `{ "error": "<message>" }`.

### 7.4 – Stub Implementations
- [ ] **Task 7.4.1**: Create `StubAvailabilityChecker.java` in `outbound.integration` – implements `AvailabilityChecker`; always returns `new AvailableCapacity(Integer.MAX_VALUE)`.
- [ ] **Task 7.4.2**: Create `LoggingDomainEventPublisher.java` in `outbound.integration` – implements `DomainEventPublisher`; constructor-inject Spring `ApplicationEventPublisher`; `publish(event)` calls `applicationEventPublisher.publishEvent(event)`.
- [ ] **Task 7.4.3**: Create `SystemClockPort.java` in `outbound.integration.clock` – implements `ClockPort`; returns `Instant.now()`.

### 7.5 – Event Listener
- [ ] **Task 7.5.1**: Create `TourBookingEventListener.java` in `listeners` – annotate method with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`; listens for `TourBookingRequested`; logs event content via SLF4J.

### 7.6 – Bootstrap
- [ ] **Task 7.6.1**: Create `AlpineBookingApplication.java` in `bootstrap` – annotate `@SpringBootApplication` with `scanBasePackages = "com.dominikgaller.alpinebooking.booking"`; contains `main` method.
- [ ] **Task 7.6.2**: Create `BookingConfig.java` in `bootstrap` – annotate `@Configuration`; declare `@Bean` methods for `StubAvailabilityChecker`, `LoggingDomainEventPublisher`, `SystemClockPort`.

### 7.7 – Web Tests
- [ ] **Task 7.7.1**: Create `TourBookingControllerTest.java` in test scope – annotate `@WebMvcTest(TourBookingController.class)`; mock `RequestTourBookingUseCase`.
- [ ] **Task 7.7.2**: Add test: POST with valid body → use case returns result → HTTP 201 with `bookingId` and `status` in response body.
- [ ] **Task 7.7.3**: Add test: POST with missing `tourId` field → HTTP 400 (Bean Validation).
- [ ] **Task 7.7.4**: Add test: POST triggers `CapacityExceededException` from use case → HTTP 409.
- [ ] **Task 7.7.5**: Add test: POST triggers `AvailabilityUnavailableException` from use case → HTTP 502.

### 7.8 – Build Verification
- [ ] **Task 7.8.1**: Run `./gradlew clean test` – all test types green (domain, use case, persistence IT, web slice).
- [ ] **Task 7.8.2**: Run `./gradlew build` – full build artifact generated successfully.