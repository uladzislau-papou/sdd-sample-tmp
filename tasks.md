# Tasks – UC05: StartTour

Based on `plan.md`.

---

## Phase 1: Domain Foundation

### 1.1 – GuideTourId + GuideTourStatus
- [x] **Task 1.1**: Create `booking.core.domain.GuideTourId` (record wrapping `UUID`, `generate()` static factory, null-check in canonical constructor).
- [x] **Task 1.2**: Create `booking.core.domain.GuideTourStatus` enum (`SCHEDULED, RUNNING, FINISHED, CANCELLED`).

### 1.2 – Domain Exceptions
- [x] **Task 1.3**: Create `booking.core.domain.exception.GuideTourNotFoundException(String guideTourId)`.
- [x] **Task 1.4**: Create `booking.core.domain.exception.InvalidGuideTourStateException(GuideTourStatus status)`.
- [x] **Task 1.5**: Create `booking.core.domain.exception.TourStartTooEarlyException(Instant scheduledStart, Instant attemptedAt)`.

### 1.3 – TourStarted Event
- [x] **Task 1.6**: Create `booking.core.domain.event.TourStarted` record (`GuideTourId guideTourId, TourId tourId, Instant startedAt`), implements `DomainEvent`.

### 1.4 – GuideTour Aggregate
- [x] **Task 1.7**: Create `booking.core.domain.GuideTour` with private constructor, `schedule(GuideTourId, TourId, Instant scheduledStart)` static factory (creates in `SCHEDULED` state, no events), `reconstitute(GuideTourId, TourId, Instant scheduledStart, GuideTourStatus, Instant startedAt)` static factory, and field accessors for all fields.
- [x] **Task 1.8**: Implement `GuideTour.start(Instant startedAt)`: guard state != `SCHEDULED` → `InvalidGuideTourStateException`; guard `startedAt.isBefore(scheduledStart)` → `TourStartTooEarlyException`; set `this.status = RUNNING`; set `this.startedAt = startedAt`; record `new TourStarted(id, tourId, startedAt)`.
- [x] **Task 1.9**: Implement `GuideTour.pullDomainEvents()` using the drain-and-clear pattern from `TourBooking` (snapshot → clear → return unmodifiable).

### 1.5 – Domain Tests
- [x] **Task 1.10**: Create `GuideTourTest` (no Spring). Cover: (a) `SCHEDULED` tour with `startedAt >= scheduledStart` → transitions to `RUNNING`, one `TourStarted` event emitted, second `pullDomainEvents()` returns empty; (b) `startedAt < scheduledStart` → `TourStartTooEarlyException`; (c) state `RUNNING` → `InvalidGuideTourStateException`; (d) state `FINISHED` → `InvalidGuideTourStateException`; (e) state `CANCELLED` → `InvalidGuideTourStateException`.
- [x] **Task 1.11**: Run `./gradlew test` — all Phase 1 tests green.

---

## Phase 2: Ports

### 2.1 – Inport Types
- [x] **Task 2.1**: Create `booking.core.inport.command.StartTourCommand` record (`String guideTourId, Optional<Instant> startedAt`).
- [x] **Task 2.2**: Create `booking.core.inport.result.StartTourResult` record (`String status`).
- [x] **Task 2.3**: Create `booking.core.inport.usecase.StartTourUseCase` interface (`StartTourResult start(StartTourCommand command)`).

### 2.2 – Outport
- [x] **Task 2.4**: Create `booking.core.outport.GuideTourRepository` interface with three methods: `void save(GuideTour)`, `Optional<GuideTour> findById(GuideTourId)`, `void update(GuideTour)`.

---

## Phase 3: Persistence

### 3.1 – DB Migration + Codegen
- [x] **Task 3.1**: Create `app/src/main/resources/db/migration/V2__DDL_create_guide_tour.sql` with table `guide_tour` (columns: `id VARCHAR(36) PK`, `tour_id VARCHAR(255)`, `scheduled_start TIMESTAMP`, `status VARCHAR(50)`, `started_at TIMESTAMP NULL`).
- [x] **Task 3.2**: Run `./gradlew flywayMigrate jooqCodegen` and verify `GuideTourRecord` exists under `build/generated-src/jooq/`.

### 3.2 – Mapper
- [x] **Task 3.3**: Create `booking.outbound.persistence.write.GuideTourMapper` (package-private class). Implement `GuideTourRecord toRecord(GuideTour)` and `GuideTour toDomain(GuideTourRecord)`. Map `scheduledStart`/`startedAt` as `LocalDateTime` in DB (UTC) ↔ `Instant` in domain. Map nullable `started_at` via `Optional.ofNullable`.

### 3.3 – Repository
- [x] **Task 3.4**: Create `booking.outbound.persistence.write.GuideTourJooqRepository` (`@Repository`, implements `GuideTourRepository`). Implement: `save` (INSERT via jOOQ), `findById` (SELECT + `Optional.ofNullable` + mapper), `update` (UPDATE `status` and `started_at` WHERE `id`).

### 3.4 – Persistence Integration Test
- [x] **Task 3.5**: Create `GuideTourJooqRepositoryIT` (uses existing `PersistenceTestApplication`, `@ActiveProfiles("test")`, `@Transactional`). Cover: `save` persists all fields correctly; `findById` returns empty for unknown id; `findById` returns aggregate after save (all fields match); `update` persists `RUNNING` status and `startedAt` after `start()` is called.
- [x] **Task 3.6**: Run `./gradlew test` — persistence IT green.

---

## Phase 4: Driver (Application Service)

### 4.1 – StartTourDriver
- [x] **Task 4.1**: Create `booking.inbound.driver.StartTourDriver` (`@Service`, `@Transactional`, implements `StartTourUseCase`). Constructor-inject `GuideTourRepository`, `DomainEventPublisher`, `ClockPort`.
- [x] **Task 4.2**: Implement `start(StartTourCommand command)`: (1) resolve `effectiveStart = command.startedAt().orElseGet(clockPort::now)`; (2) parse `GuideTourId` from UUID string; (3) load aggregate via `findById` → throw `GuideTourNotFoundException` if absent; (4) call `guideTour.start(effectiveStart)`; (5) call `guideTourRepository.update(guideTour)`; (6) call `guideTour.pullDomainEvents().forEach(domainEventPublisher::publish)`; (7) return `new StartTourResult(guideTour.status().name())`.

### 4.2 – Driver Tests
- [x] **Task 4.3**: Create `StartTourDriverTest` (no Spring, inline stubs). Stub classes needed: `GuideTourStubRepository` (preload + track updates), `PublishCapturingPublisher`, `FixedClockPort`.
- [x] **Task 4.4**: Test: happy path returns `StartTourResult` with status `"RUNNING"`.
- [x] **Task 4.5**: Test: `update` is called on repository with the mutated aggregate.
- [x] **Task 4.6**: Test: `TourStarted` event is published via `DomainEventPublisher`.
- [x] **Task 4.7**: Test: when `startedAt` is `Optional.empty()`, `clockPort.now()` is used as effective start.
- [x] **Task 4.8**: Test: unknown id → `GuideTourNotFoundException` thrown.
- [x] **Task 4.9**: Test: aggregate in `RUNNING` state → `InvalidGuideTourStateException` thrown.
- [x] **Task 4.10**: Test: `startedAt` before `scheduledStart` → `TourStartTooEarlyException` thrown.
- [x] **Task 4.11**: Run `./gradlew test` — all driver tests green.

---

## Phase 5: REST Layer

### 5.1 – DTOs
- [x] **Task 5.1**: Create `booking.inbound.rest.request.StartTourRequest` record (`Instant startedAt` — nullable, no `@NotNull`).
- [x] **Task 5.2**: Create `booking.inbound.rest.response.StartTourResponse` record (`String status`).

### 5.2 – REST API Interface
- [x] **Task 5.3**: Create `booking.inbound.rest.GuideTourRestAPI` interface (`@RequestMapping("/api/v1/guide-tours")`). Add: `@PostMapping("/{guideTourId}/start")` returning `StartTourResponse`, with `@PathVariable String guideTourId` and `@RequestBody(required = false) StartTourRequest request`.

### 5.3 – Controller
- [x] **Task 5.4**: Create `booking.inbound.rest.GuideTourController` (`@RestController`, implements `GuideTourRestAPI`). Constructor-inject `StartTourUseCase`. Map nullable request → `Optional<Instant>` → `StartTourCommand`; delegate to use case; map result → `StartTourResponse`.

### 5.4 – Exception Handler
- [x] **Task 5.5**: Add three handlers to `BookingExceptionHandler`: `GuideTourNotFoundException → @ResponseStatus(NOT_FOUND)`, `InvalidGuideTourStateException → @ResponseStatus(CONFLICT)`, `TourStartTooEarlyException → @ResponseStatus(CONFLICT)`.

### 5.5 – Controller Test
- [x] **Task 5.6**: Create `GuideTourControllerTest` (`@SpringBootTest(classes = AlpineBookingApplication.class)`, `@AutoConfigureMockMvc`, `@MockitoBean StartTourUseCase`). Cover: (a) POST with no body → 200, status `"RUNNING"`; (b) POST with `startedAt` in body → 200; (c) `GuideTourNotFoundException` → 404 with `$.error`; (d) `InvalidGuideTourStateException` → 409 with `$.error`; (e) `TourStartTooEarlyException` → 409 with `$.error`.
- [x] **Task 5.7**: Run `./gradlew test` — all REST tests green.

---

## Phase 6: HTTP File + Final Verification

### 6.1 – HTTP Client File
- [x] **Task 6.1**: Create `rest/uc05-start-tour.http`. Include five requests: (1) happy path no body; (2) happy path with explicit `startedAt`; (3) 404 unknown guideTourId; (4) 409 invalid state; (5) 409 too early.

### 6.2 – Full Build
- [x] **Task 6.2**: Run `./gradlew clean test` — all tests green.
- [x] **Task 6.3**: Run `./gradlew build` — build passes.

### 6.3 – Spec Closeout
- [x] **Task 6.4**: Update `documentation/use-cases/uc05-start-tour.spec.md` status to `IMPLEMENTED`.
