# Plan – UC01: RequestTourBooking

## 1. Analysis Summary

### 1.1 Impacted Building Blocks

| Building Block | Name | Status |
|---|---|---|
| Aggregate | `TourBooking` | spec exists, no implementation |
| Value Objects | `BookingId`, `TourId`, `TourDate`, `ParticipantCount`, `AvailableCapacity`, `ParticipantContact` | partially specified, no implementation |
| Domain Event | `TourBookingRequested` | spec exists, no implementation |
| Inbound Port | `RequestTourBookingUseCase` | **missing spec**, no implementation |
| Outbound Port | `TourBookingRepository` | **missing spec**, no implementation |
| Outbound Port | `AvailabilityChecker` | **missing spec**, no implementation |
| Outbound Port | `DomainEventPublisher` | **missing spec**, no implementation |
| Outbound Port | `ClockPort` | referenced in architecture, **missing spec**, no implementation |
| Application Service | `RequestTourBookingDriver` | no implementation |
| Persistence Adapter | `TourBookingJooqRepository` | no implementation |
| REST Adapter | `TourBookingController` | no implementation |
| Build Setup | Spring Boot, jOOQ, Flyway, H2, AssertJ | **not configured** – build.gradle.kts is a Gradle init skeleton |

### 1.2 Domain Invariants Affected

All invariants of the `TourBooking.request()` factory method:
- `tourDate` must be in the future at creation time
- `participantCount` >= 1
- `participantCount` <= `availableCapacity`
- Status initialized to `REQUESTED`

### 1.3 ADR Check

| Decision | ADR Status |
|---|---|
| Core tech stack (Java 21, Spring Boot, jOOQ, Flyway, H2, AssertJ) | ADR 0001 – Accepted |
| Domain event publication strategy (post-commit) | **ADR 0002 – Missing** |

---

## 2. Missing Specifications

The following specs must be created before or alongside implementation.
They are included in the task list (Step 1).

### 2.1 Port Specifications (new directory: `documentation/ports/`)

#### `request-tour-booking.inport.md`
**Inport: `RequestTourBookingUseCase`**
- Method: `RequestTourBookingResult request(RequestTourBookingCommand command)`
- `RequestTourBookingCommand` fields: `tourId` (String), `tourDate` (LocalDate), `participantCount` (int), `contactName` (String), `contactEmail` (String)
- `RequestTourBookingResult` fields: `bookingId` (String), `status` (String)
- Throws: `CapacityExceededException`, `InvalidBookingRequestException`
- Transactional: yes (boundary at driver)

#### `tour-booking-repository.outport.md`
**Outport: `TourBookingRepository`**
- Method: `void save(TourBooking booking)`
- Persists a new `TourBooking` aggregate atomically.
- No `update` method required for UC01.
- Idempotency: not required at this scope.

#### `availability-checker.outport.md`
**Outport: `AvailabilityChecker`**
- Method: `AvailableCapacity checkAvailability(TourId tourId, TourDate tourDate)`
- Returns `AvailableCapacity` (wraps int).
- Throws: `AvailabilityUnavailableException` on infrastructure failure.
- Note: Does NOT throw on capacity exceeded – the aggregate enforces the invariant.
- Reference implementation: `StubAvailabilityChecker` – returns fixed unlimited capacity.

#### `domain-event-publisher.outport.md`
**Outport: `DomainEventPublisher`**
- Method: `void publish(TourBookingRequested event)`
- Called by the driver WITHIN the transaction (see ADR 0002 for strategy).
- Reference implementation: `LoggingDomainEventPublisher` – logs event to SLF4J.

#### `clock.outport.md`
**Outport: `ClockPort`**
- Method: `Instant now()`
- Used by the driver to provide current time to the aggregate factory.
- Reference implementation: `SystemClockPort` – delegates to `Instant.now()`.

### 2.2 Specification Gaps in Existing Docs (to be supplemented)

#### `ParticipantContact` structure (not defined in domain spec)
Proposed value object fields:
- `name`: String (non-blank)
- `email`: String (non-blank, basic format check)

This supplements `documentation/domain/aggregate-tour-booking.spec.md`.

#### `AvailableCapacity` value object (not mentioned in domain spec)
- Wraps an `int`.
- Invariant: value >= 0.
- Required to store the max capacity snapshot in the aggregate at booking time.

This supplements `documentation/domain/aggregate-tour-booking.spec.md`.

#### REST endpoint contract (not defined in UC01 spec)
Proposed HTTP contract:
- Method: `POST`
- Path: `/api/v1/bookings`
- Request body: `{ "tourId": "...", "tourDate": "YYYY-MM-DD", "participantCount": N, "contactName": "...", "contactEmail": "..." }`
- Response body (201 Created): `{ "bookingId": "...", "status": "REQUESTED" }`
- Error responses: `400 Bad Request` (validation), `409 Conflict` (capacity exceeded), `502 Bad Gateway` (availability check failure)

### 2.3 Missing ADR

#### ADR 0002 – Domain Event Publication Strategy

**Context:** `modelling.definition.md` states that domain events MUST be published AFTER successful transaction commit. Spring `@Transactional` boundaries make this non-trivial.

**Decision (proposed):** The driver calls `DomainEventPublisher.publish(event)` **inside** the `@Transactional` boundary. The reference implementation of `DomainEventPublisher` delegates to Spring's `ApplicationEventPublisher`. A `@TransactionalEventListener(phase = AFTER_COMMIT)` listener in the `listeners` package receives the Spring application event and performs the actual side-effect (logging for now). This satisfies the post-commit requirement without an external broker.

**ADR Status:** Must be created and confirmed before implementation of Step 5 (Driver).

---

## 3. Risks

| Risk | Mitigation |
|---|---|
| jOOQ code generation requires Flyway migration to run first during build | Configure jOOQ generation as a Gradle task that depends on the Flyway migration task |
| Spring Boot 4 with Gradle 9 compatibility | Already validated in ADR 0001 context |
| H2 and Flyway version alignment | Pin versions explicitly in `libs.versions.toml`; verify during build setup |
| `AvailableCapacity` stored in aggregate requires a DB column | Include in the Flyway migration from the start |

---

## 4. Acceptance Criteria

### AC-01 – Happy Path
**Given** valid input with available capacity
**When** `POST /api/v1/bookings` is called
**Then** a booking is stored in `REQUESTED` state, `bookingId` is returned in the response with HTTP 201, and `TourBookingRequested` is published after commit.

### AC-02 – Capacity Exceeded
**Given** the availability checker signals full capacity
**When** `POST /api/v1/bookings` is called
**Then** HTTP 409 is returned and no booking is persisted.

### AC-03 – Invalid Participant Count
**Given** `participantCount < 1`
**When** `POST /api/v1/bookings` is called
**Then** HTTP 400 is returned and no booking is persisted.

### AC-04 – Invalid Tour Date
**Given** `tourDate` is in the past
**When** `POST /api/v1/bookings` is called
**Then** HTTP 400 is returned and no booking is persisted.

### AC-05 – Availability Check Failure
**Given** the availability checker throws an infrastructure exception
**When** `POST /api/v1/bookings` is called
**Then** HTTP 502 is returned and no booking is persisted.

---

## 5. Implementation Steps

### Step 1 – Specification Work (Docs Only)

Create missing documentation. No Java code.

Files to create:
- `documentation/ports/request-tour-booking.inport.md`
- `documentation/ports/tour-booking-repository.outport.md`
- `documentation/ports/availability-checker.outport.md`
- `documentation/ports/domain-event-publisher.outport.md`
- `documentation/ports/clock.outport.md`
- `documentation/adr/0002-domain-event-publication.adr.md`

Files to update:
- `documentation/domain/aggregate-tour-booking.spec.md` – add `ParticipantContact` fields and `AvailableCapacity` VO
- `documentation/use-cases/uc01-request-tour-booking.spec.md` – add REST contract section

---

### Step 2 – Build Setup

Replace the Gradle init skeleton with a full Spring Boot application build.

Files to change:
- `app/build.gradle.kts` – add Spring Boot plugin, Spring Boot Web/Test starters, jOOQ, Flyway, H2, AssertJ; configure jOOQ code gen from H2 after Flyway
- `gradle/libs.versions.toml` – add versions for Spring Boot, jOOQ, Flyway, H2, AssertJ
- `settings.gradle.kts` – verify project name
- `app/src/main/resources/application.yml` – H2 datasource, Flyway, jOOQ config
- `app/src/test/resources/application-test.yml` – in-memory H2 for tests

Remove:
- `app/src/main/java/org/example/App.java`
- `app/src/test/java/org/example/AppTest.java`

---

### Step 3 – Domain Layer

Base package: `com.dominikgaller.alpinebooking.booking.core.domain`

Files to create:
- `BookingId.java` – record, wraps UUID, validates non-null
- `TourId.java` – record, wraps String, validates non-blank
- `TourDate.java` – record, wraps LocalDate, validates non-null; method `isInFuture(Instant now)`
- `ParticipantCount.java` – record, wraps int, invariant: value >= 1
- `AvailableCapacity.java` – record, wraps int, invariant: value >= 0
- `ParticipantContact.java` – record, fields: `name` (non-blank), `email` (non-blank)
- `TourBookingStatus.java` – enum: REQUESTED, CONFIRMED, CANCELLED, ACTIVE, COMPLETED
- `event/TourBookingRequested.java` – record: bookingId, tourId, tourDate, participantCount, occurredAt
- `TourBooking.java` – aggregate root with `request(...)` static factory; enforces all invariants; records domain events

`TourBooking.request(...)` signature:
```java
public static TourBooking request(
    BookingId bookingId,
    TourId tourId,
    TourDate tourDate,
    ParticipantCount participantCount,
    AvailableCapacity availableCapacity,
    Instant now
)
```

Enforces:
1. `tourDate.isInFuture(now)` → else throws `InvalidBookingRequestException`
2. `participantCount.value() <= availableCapacity.value()` → else throws `CapacityExceededException`
3. Initializes status = `REQUESTED`
4. Records `TourBookingRequested` domain event

Domain exceptions to create (in `core.domain` or sub-package):
- `CapacityExceededException` (domain exception, maps to HTTP 409)
- `InvalidBookingRequestException` (domain exception, maps to HTTP 400)

---

### Step 4 – Port Interfaces

#### Inbound (`com.dominikgaller.alpinebooking.booking.core.inport`)
Files to create:
- `RequestTourBookingCommand.java` – record: tourId, tourDate, participantCount, contactName, contactEmail
- `RequestTourBookingResult.java` – record: bookingId, status
- `RequestTourBookingUseCase.java` – interface: `RequestTourBookingResult request(RequestTourBookingCommand command)`

#### Outbound (`com.dominikgaller.alpinebooking.booking.core.outport`)
Files to create:
- `TourBookingRepository.java` – interface: `void save(TourBooking booking)`
- `AvailabilityChecker.java` – interface: `AvailableCapacity checkAvailability(TourId tourId, TourDate tourDate)`
- `DomainEventPublisher.java` – interface: `void publish(TourBookingRequested event)`
- `ClockPort.java` – interface: `Instant now()`

---

### Step 5 – Application Service (Driver)

Package: `com.dominikgaller.alpinebooking.booking.inbound.driver`

File to create:
- `RequestTourBookingDriver.java` – implements `RequestTourBookingUseCase`
  - Annotated: `@Service`, `@Transactional`
  - Constructor-injected: `TourBookingRepository`, `AvailabilityChecker`, `DomainEventPublisher`, `ClockPort`
  - Orchestration:
    1. Map `RequestTourBookingCommand` to domain types
    2. Generate `BookingId` (UUID)
    3. Call `clockPort.now()`
    4. Call `availabilityChecker.checkAvailability(tourId, tourDate)`
    5. Call `TourBooking.request(...)` factory
    6. Call `tourBookingRepository.save(booking)`
    7. Collect domain events from booking
    8. Call `domainEventPublisher.publish(event)` for each event
    9. Return `RequestTourBookingResult`

---

### Step 6 – Persistence Adapter

**6a – Flyway Migration**

File to create:
- `app/src/main/resources/db/migration/V1__DDL_create_tour_booking.sql`

Schema:
```sql
CREATE TABLE tour_booking (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,
    tour_id          VARCHAR(255) NOT NULL,
    tour_date        DATE         NOT NULL,
    participant_count INT          NOT NULL,
    available_capacity INT         NOT NULL,
    contact_name     VARCHAR(255) NOT NULL,
    contact_email    VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL
);
```

**6b – jOOQ Code Generation**

After migration is in place, trigger jOOQ generation. Output goes to `app/build/generated-src/jooq/`.

**6c – Repository Adapter**

Package: `com.dominikgaller.alpinebooking.booking.outbound.persistence.write`

Files to create:
- `TourBookingJooqRepository.java` – implements `TourBookingRepository`
  - Uses generated jOOQ `TourBookingRecord`
  - Maps domain `TourBooking` → jOOQ record → SQL INSERT
- `TourBookingMapper.java` – pure mapping component (domain ↔ jOOQ record)

---

### Step 7 – REST Adapter, Error Mapping & Bootstrap

**REST Adapter**

Package: `com.dominikgaller.alpinebooking.booking.inbound.rest`

Files to create:
- `RequestTourBookingRequest.java` – record: tourId, tourDate, participantCount, contactName, contactEmail
- `RequestTourBookingResponse.java` – record: bookingId, status
- `TourBookingController.java` – `@RestController`; maps POST `/api/v1/bookings` → `RequestTourBookingUseCase`
- `BookingExceptionHandler.java` – `@RestControllerAdvice`; maps domain exceptions to HTTP status codes

**Stub Implementations (outbound/integration)**

Package: `com.dominikgaller.alpinebooking.booking.outbound.integration`

Files to create:
- `StubAvailabilityChecker.java` – returns `AvailableCapacity(Integer.MAX_VALUE)`
- `LoggingDomainEventPublisher.java` – logs event via SLF4J; uses Spring `ApplicationEventPublisher` (see ADR 0002)

Package: `com.dominikgaller.alpinebooking.booking.outbound.integration.clock`

Files to create:
- `SystemClockPort.java` – returns `Instant.now()`

**Event Listener** (ADR 0002 implementation)

Package: `com.dominikgaller.alpinebooking.booking.listeners`

Files to create:
- `TourBookingEventListener.java` – `@TransactionalEventListener(phase = AFTER_COMMIT)` on `TourBookingRequested`; logs event

**Bootstrap**

Package: `com.dominikgaller.alpinebooking.booking.bootstrap`

Files to create:
- `AlpineBookingApplication.java` – `@SpringBootApplication`
- `BookingConfig.java` – `@Configuration`; wires `StubAvailabilityChecker`, `LoggingDomainEventPublisher`, `SystemClockPort`

---

### Step 8 – Tests

#### Domain Tests
Package: `com.dominikgaller.alpinebooking.booking.core.domain` (test scope)

Files to create:
- `TourBookingTest.java`
  - Happy path: `request()` creates booking in REQUESTED state
  - `request()` emits `TourBookingRequested`
  - Rejects participantCount < 1
  - Rejects participantCount > availableCapacity (CapacityExceeded)
  - Rejects past tourDate
- `TourDateTest.java` – valid/invalid construction
- `ParticipantCountTest.java` – valid/invalid construction
- `ParticipantContactTest.java` – valid/invalid construction
- `BookingIdTest.java` – construction and equality

#### Use Case Tests
Package: `com.dominikgaller.alpinebooking.booking.inbound.driver` (test scope)

Files to create:
- `RequestTourBookingDriverTest.java` (Spring-free, stub-based)
  - Happy path: booking saved, event published
  - Capacity exceeded: exception propagated, save not called
  - Availability failure: exception propagated, save not called

#### Persistence Integration Tests
Package: `com.dominikgaller.alpinebooking.booking.outbound.persistence.write` (test scope)

Files to create:
- `TourBookingJooqRepositoryIT.java` (`@SpringBootTest` or slice + H2 + Flyway)
  - `save` persists all fields correctly
  - Roundtrip: save → query → verify mapping

#### Web Tests
Package: `com.dominikgaller.alpinebooking.booking.inbound.rest` (test scope)

Files to create:
- `TourBookingControllerTest.java` (`@WebMvcTest`)
  - POST with valid body → 201 with bookingId
  - POST with missing fields → 400
  - POST triggers CapacityExceededException → 409
  - POST triggers AvailabilityUnavailableException → 502

---

## 6. Affected Files Summary

### New Documentation
```
documentation/ports/request-tour-booking.inport.md
documentation/ports/tour-booking-repository.outport.md
documentation/ports/availability-checker.outport.md
documentation/ports/domain-event-publisher.outport.md
documentation/ports/clock.outport.md
documentation/adr/0002-domain-event-publication.adr.md
```

### Updated Documentation
```
documentation/domain/aggregate-tour-booking.spec.md
documentation/use-cases/uc01-request-tour-booking.spec.md
```

### Build Files
```
app/build.gradle.kts
gradle/libs.versions.toml
settings.gradle.kts
app/src/main/resources/application.yml
app/src/test/resources/application-test.yml
```

### Migration
```
app/src/main/resources/db/migration/V1__DDL_create_tour_booking.sql
```

### Production Java
```
app/src/main/java/com/dominikgaller/alpinebooking/booking/bootstrap/AlpineBookingApplication.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/bootstrap/BookingConfig.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/BookingId.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/TourId.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/TourDate.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/ParticipantCount.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/AvailableCapacity.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/ParticipantContact.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/TourBookingStatus.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/TourBooking.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/event/TourBookingRequested.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/exception/CapacityExceededException.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/domain/exception/InvalidBookingRequestException.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/inport/RequestTourBookingCommand.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/inport/RequestTourBookingResult.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/inport/RequestTourBookingUseCase.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/outport/TourBookingRepository.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/outport/AvailabilityChecker.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/outport/DomainEventPublisher.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/core/outport/ClockPort.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/inbound/driver/RequestTourBookingDriver.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/inbound/rest/RequestTourBookingRequest.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/inbound/rest/RequestTourBookingResponse.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/inbound/rest/TourBookingController.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/inbound/rest/BookingExceptionHandler.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/outbound/persistence/write/TourBookingJooqRepository.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/outbound/persistence/write/TourBookingMapper.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/outbound/integration/StubAvailabilityChecker.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/outbound/integration/LoggingDomainEventPublisher.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/outbound/integration/clock/SystemClockPort.java
app/src/main/java/com/dominikgaller/alpinebooking/booking/listeners/TourBookingEventListener.java
```

### Test Java
```
app/src/test/java/com/dominikgaller/alpinebooking/booking/core/domain/TourBookingTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/core/domain/TourDateTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/core/domain/ParticipantCountTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/core/domain/ParticipantContactTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/core/domain/BookingIdTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/inbound/driver/RequestTourBookingDriverTest.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/outbound/persistence/write/TourBookingJooqRepositoryIT.java
app/src/test/java/com/dominikgaller/alpinebooking/booking/inbound/rest/TourBookingControllerTest.java
```

---

## 7. Quality Gate

Must pass before task is complete:
```shell
./gradlew clean test
./gradlew build
```

All tests must be green. No disabled or placeholder tests.