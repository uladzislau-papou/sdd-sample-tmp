# Plan – UC05: StartTour

## Status
PLANNED

## Reference
- Spec: `documentation/use-cases/uc05-start-tour.spec.md`
- Architecture: `documentation/architecture.definition.md`
- Domain: `documentation/modelling.definition.md`

---

## 1. Clarifications & Spec Gaps Resolved

### 1.1 GuideTour is a New Aggregate
The spec references a `GuideTour` aggregate that does not yet exist. `TourBooking` represents the
customer-side booking. `GuideTour` is the guide-side tour execution record — a separate aggregate
with its own identity, state machine, and lifecycle.

**Decision**: Introduce `GuideTour` as a new aggregate root within the existing `booking` bounded
context. No new bounded context is introduced at this stage.

### 1.2 State Naming
Spec says "READY (or SCHEDULED)" and "RUNNING (or ACTIVE)".

**Decision**:
- Pre-start state: `SCHEDULED`
- Post-start state: `RUNNING` — avoids confusion with `TourBookingStatus.ACTIVE` (booking side)
- Full enum: `SCHEDULED → RUNNING → FINISHED | CANCELLED`

### 1.3 TooEarly Rule
Spec says "Current date/time >= tourStartTime (or within allowed start window)" but gives no
tolerance window.

**Decision**: `GuideTour` stores a `scheduledStart` (Instant). Start is only allowed when
`startedAt >= scheduledStart`. Any earlier attempt throws `TourStartTooEarlyException`. No
tolerance window — exact or late starts only.

### 1.4 Optional startedAt
Spec says "startedAt (optional; default = now)".

**Decision**:
- REST: optional JSON body with nullable `startedAt` (ISO-8601 Instant string).
- Inport command: `StartTourCommand(String guideTourId, Optional<Instant> startedAt)`.
- Driver resolves: `startedAt.orElseGet(clockPort::now)` before calling the aggregate.

### 1.5 InvalidState Errors
Any state other than `SCHEDULED` is invalid. Throws `InvalidGuideTourStateException` → HTTP 409.

### 1.6 Actor Authorization
Spec says "Optional – Actor is authorized guide". **Out of scope** — no auth layer exists.

### 1.7 REST Resource Design
New `GuideTourRestAPI` interface + `GuideTourController` (not added to `TourBookingRestAPI`).
`GuideTour` is a distinct resource from `TourBooking`.

Route: `POST /api/v1/guide-tours/{guideTourId}/start`
HTTP 200 on success (state transition, not creation).

### 1.8 GuideTour Data Model
Fields: `GuideTourId id`, `TourId tourId`, `Instant scheduledStart`, `GuideTourStatus status`,
`Instant startedAt` (null until started — stored as nullable column).

### 1.9 ADR needed?
No ADR required. The new aggregate follows the established Hexagonal + DDD pattern exactly.
The decision to stay in the `booking` bounded context is a pragmatic scope decision, not an
architectural deviation.

---

## 2. Acceptance Criteria

- [ ] `POST /api/v1/guide-tours/{id}/start` on a SCHEDULED tour returns 200 with status `RUNNING`
- [ ] `TourStarted` domain event is published after commit
- [ ] GuideTour is persisted with updated status and startedAt
- [ ] Start before scheduledStart returns 409
- [ ] Start on a non-SCHEDULED tour (RUNNING / FINISHED / CANCELLED) returns 409
- [ ] Start on non-existent guideTourId returns 404
- [ ] If startedAt omitted, ClockPort.now() is used
- [ ] `./gradlew clean test` and `./gradlew build` pass

---

## 3. New Components Overview

### Domain (`booking.core.domain`)
| Component | Type | Notes |
|---|---|---|
| `GuideTourId` | record (VO) | wraps UUID; `generate()` factory |
| `GuideTourStatus` | enum | `SCHEDULED, RUNNING, FINISHED, CANCELLED` |
| `GuideTour` | class (aggregate) | `schedule()` + `reconstitute()` + `start(Instant)` |
| `TourStarted` | record (domain event) | `GuideTourId, TourId, Instant startedAt`; implements `DomainEvent` |
| `GuideTourNotFoundException` | exception | → HTTP 404 |
| `InvalidGuideTourStateException` | exception | → HTTP 409 |
| `TourStartTooEarlyException` | exception | → HTTP 409 |

### Inport (`booking.core.inport`)
| Component | Type | Notes |
|---|---|---|
| `StartTourCommand` | record | `String guideTourId, Optional<Instant> startedAt` |
| `StartTourResult` | record | `String status` |
| `StartTourUseCase` | interface | `start(StartTourCommand) → StartTourResult` |

### Outport (`booking.core.outport`)
| Component | Type | Notes |
|---|---|---|
| `GuideTourRepository` | interface | `save`, `findById(GuideTourId) → Optional<GuideTour>`, `update` |

### DB
| File | Notes |
|---|---|
| `V2__DDL_create_guide_tour.sql` | New `guide_tour` table |

```sql
CREATE TABLE guide_tour (
    id               VARCHAR(36)  NOT NULL,
    tour_id          VARCHAR(255) NOT NULL,
    scheduled_start  TIMESTAMP    NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    started_at       TIMESTAMP    NULL,
    CONSTRAINT pk_guide_tour PRIMARY KEY (id)
);
```

### Persistence (`booking.outbound.persistence.write`)
| Component | Type | Notes |
|---|---|---|
| `GuideTourMapper` | class (package-private) | `GuideTourRecord` ↔ `GuideTour` |
| `GuideTourJooqRepository` | @Repository | implements `GuideTourRepository` |

### Driver (`booking.inbound.driver`)
| Component | Type | Notes |
|---|---|---|
| `StartTourDriver` | @Service @Transactional | implements `StartTourUseCase` |

### REST (`booking.inbound.rest`)
| Component | Type | Notes |
|---|---|---|
| `StartTourRequest` | record | nullable `Instant startedAt` |
| `StartTourResponse` | record | `String status` |
| `GuideTourRestAPI` | interface | HTTP contract |
| `GuideTourController` | @RestController | implements `GuideTourRestAPI` |

---

## 4. Implementation Steps

### Step 1 – Domain: `GuideTourId`
Create `booking.core.domain.GuideTourId`:
- `record GuideTourId(UUID value)`
- `static GuideTourId generate()`
- Validate non-null in canonical constructor

### Step 2 – Domain: `GuideTourStatus`
Create `booking.core.domain.GuideTourStatus`:
- `SCHEDULED, RUNNING, FINISHED, CANCELLED`

### Step 3 – Domain: Exceptions
Create in `booking.core.domain.exception`:
- `GuideTourNotFoundException(String guideTourId)`
- `InvalidGuideTourStateException(GuideTourStatus status)`
- `TourStartTooEarlyException(Instant scheduledStart, Instant attemptedAt)`

### Step 4 – Domain: `TourStarted` event
Create `booking.core.domain.event.TourStarted`:
- `record TourStarted(GuideTourId guideTourId, TourId tourId, Instant startedAt) implements DomainEvent`

### Step 5 – Domain: `GuideTour` aggregate
Create `booking.core.domain.GuideTour`:
- Private constructor
- `static GuideTour schedule(GuideTourId, TourId, Instant scheduledStart)` — creates in SCHEDULED state
- `static GuideTour reconstitute(GuideTourId, TourId, Instant scheduledStart, GuideTourStatus, Instant startedAt)`
- `void start(Instant startedAt)`:
  1. if `status != SCHEDULED` → throw `InvalidGuideTourStateException`
  2. if `startedAt.isBefore(scheduledStart)` → throw `TourStartTooEarlyException`
  3. `this.status = RUNNING; this.startedAt = startedAt`
  4. record `new TourStarted(id, tourId, startedAt)`
- `pullDomainEvents()` — same pattern as `TourBooking`
- Accessors for all fields

### Step 6 – Domain Test: `GuideTourTest`
Create `GuideTourTest` (no Spring):
- `start_transitionsToRunning_andEmitsTourStarted` — scheduledStart in past
- `start_throwsTourStartTooEarlyException_whenBeforeScheduledStart`
- `start_throwsInvalidGuideTourStateException_whenAlreadyRunning`
- `start_throwsInvalidGuideTourStateException_whenCancelled`

### Step 7 – Inport: Command / Result / UseCase
Create in `booking.core.inport`:
- `command/StartTourCommand.java` — `record StartTourCommand(String guideTourId, Optional<Instant> startedAt)`
- `result/StartTourResult.java` — `record StartTourResult(String status)`
- `usecase/StartTourUseCase.java` — `StartTourResult start(StartTourCommand command)`

### Step 8 – Outport: `GuideTourRepository`
Create `booking.core.outport.GuideTourRepository`:
```java
void save(GuideTour guideTour);
Optional<GuideTour> findById(GuideTourId guideTourId);
void update(GuideTour guideTour);
```

### Step 9 – DB Migration
Create `app/src/main/resources/db/migration/V2__DDL_create_guide_tour.sql` with the `guide_tour`
table schema above.

After creating the file, run: `./gradlew flywayMigrate jooqCodegen`
Verify `GuideTourRecord` is generated at
`build/generated-src/jooq/main/.../jooq/tables/records/GuideTourRecord.java` before proceeding.

### Step 10 – Persistence: `GuideTourMapper` + `GuideTourJooqRepository`
Create `booking.outbound.persistence.write.GuideTourMapper` (package-private):
- `GuideTourRecord toRecord(GuideTour)`
- `GuideTour toDomain(GuideTourRecord)` — uses `reconstitute()`, maps nullable `started_at` via
  `Optional.ofNullable(record.getStartedAt()).map(ts -> ts.toInstant(ZoneOffset.UTC))`

Create `booking.outbound.persistence.write.GuideTourJooqRepository` (@Repository):
- `save`: `dsl.insertInto(GUIDE_TOUR).set(mapper.toRecord(g)).execute()`
- `findById`: select + map via `mapper.toDomain()`
- `update`: `dsl.update(GUIDE_TOUR).set(...).where(GUIDE_TOUR.ID.eq(...)).execute()`

### Step 11 – Persistence IT: `GuideTourJooqRepositoryIT`
Create in `booking.outbound.persistence.write`:
- `save_persistsAllFields` — verify all columns written correctly
- `findById_returnsEmpty_whenNotFound`
- `findById_returnsAggregate_afterSave`
- `update_changesStatus_andStartedAt_afterStart`

Uses `PersistenceTestApplication` + `@ActiveProfiles("test")` + `@Transactional`.

### Step 12 – Driver: `StartTourDriver`
Create `booking.inbound.driver.StartTourDriver` (@Service @Transactional):
```
1. effectiveStart = command.startedAt().orElseGet(clockPort::now)
2. guideTourId = new GuideTourId(UUID.fromString(command.guideTourId()))
3. guideTour = guideTourRepository.findById(guideTourId) → throw GuideTourNotFoundException if absent
4. guideTour.start(effectiveStart)  ← domain enforces rules
5. guideTourRepository.update(guideTour)
6. guideTour.pullDomainEvents().forEach(domainEventPublisher::publish)
7. return new StartTourResult(guideTour.status().name())
```
Injects: `GuideTourRepository`, `DomainEventPublisher`, `ClockPort`.

### Step 13 – Driver Test: `StartTourDriverTest`
Create `StartTourDriverTest` (no Spring, inline stubs):
- `start_returnsRunningStatus_onScheduledTour`
- `start_callsUpdateOnRepository`
- `start_publishesTourStartedEvent`
- `start_usesClockNow_whenStartedAtAbsent`
- `start_throwsGuideTourNotFoundException_whenNotFound`
- `start_throwsInvalidGuideTourStateException_whenAlreadyRunning`
- `start_throwsTourStartTooEarlyException_whenBeforeScheduledStart`

Stubs needed: `GuideTourStubRepository`, `PublishCapturingPublisher` (reuse pattern from
`ChangeParticipantsDriverTest`), `FixedClockPort`.

### Step 14 – REST: DTOs
Create in `booking.inbound.rest`:
- `request/StartTourRequest.java` — `record StartTourRequest(Instant startedAt)` (nullable, no
  `@NotNull` — absence means use clock)
- `response/StartTourResponse.java` — `record StartTourResponse(String status)`

### Step 15 – REST: `GuideTourRestAPI` interface
Create `booking.inbound.rest.GuideTourRestAPI`:
```java
@RequestMapping("/api/v1/guide-tours")
public interface GuideTourRestAPI {
    @PostMapping("/{guideTourId}/start")
    StartTourResponse start(
        @PathVariable String guideTourId,
        @RequestBody(required = false) StartTourRequest request);
}
```
No `@Valid` needed (no bean validation on nullable body).

### Step 16 – REST: `GuideTourController`
Create `booking.inbound.rest.GuideTourController` (@RestController implements GuideTourRestAPI):
- Map `StartTourRequest` (nullable) → `StartTourCommand`
- `Optional<Instant> startedAt = request != null ? Optional.ofNullable(request.startedAt()) : Optional.empty()`
- Delegate to `StartTourUseCase.start(command)`
- Map result → `StartTourResponse`

### Step 17 – Exception Handler: update `BookingExceptionHandler`
Add three handlers:
- `GuideTourNotFoundException` → `@ResponseStatus(HttpStatus.NOT_FOUND)`
- `InvalidGuideTourStateException` → `@ResponseStatus(HttpStatus.CONFLICT)`
- `TourStartTooEarlyException` → `@ResponseStatus(HttpStatus.CONFLICT)`

### Step 18 – Controller Test: `GuideTourControllerTest`
Create `GuideTourControllerTest` (@SpringBootTest + @AutoConfigureMockMvc):
- `start_returns200_withRunningStatus` (happy path with no body → uses clock)
- `start_returns200_withRunningStatus_whenStartedAtProvided`
- `start_returns404_whenNotFound`
- `start_returns409_whenInvalidState`
- `start_returns409_whenTooEarly`

Uses `@MockitoBean StartTourUseCase`.

### Step 19 – HTTP File
Create `rest/uc05-start-tour.http`:
- Happy path (no body)
- Happy path with explicit startedAt
- 404: unknown guideTourId
- 409: invalid state
- 409: too early

### Step 20 – Verify
```
./gradlew clean test
./gradlew build
```

### Step 21 – Update Spec Status
Update `documentation/use-cases/uc05-start-tour.spec.md`: `Status: IMPLEMENTED`

---

## 5. Risks

| Risk | Mitigation |
|---|---|
| `TIMESTAMP` storage for `Instant` in H2 | Map with `LocalDateTime.ofInstant(instant, UTC)` on write; `record.getStartedAt().toInstant(UTC)` on read |
| jOOQ codegen fails if migration doesn't apply cleanly | Run `./gradlew flywayMigrate jooqCodegen` after Step 9 and confirm `GuideTourRecord` exists before writing persistence code |
| `BookingExceptionHandler` catches all — adding GuideTour exceptions mixes concerns | Acceptable now; can be split into a dedicated `GuideTourExceptionHandler` if handler grows |
| `@RequestBody(required = false)` behaviour in Spring Boot 4.x | Verify no body = `null` request object is correctly handled in controller; add controller test for both cases |
