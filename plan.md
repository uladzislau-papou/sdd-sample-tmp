# Plan – Extract Guide Bounded Context

## Motivation

`GuideTour` and all related code currently lives in the `booking` bounded context.
UC06 confirms the boundary: `guideTourId` is a plain string correlation ID in `booking` —
proof that `booking` has no business knowing about `GuideTour` internals.

ADR: `documentation/adr/0003-separate-guide-bounded-context.adr.md`

---

## Target Structure

```
com.dominikgaller.alpinebooking
├── bootstrap
│   ├── AlpineBookingApplication
│   ├── BookingConfig           (unchanged)
│   └── GuideConfig   (new)
├── shared.domain
│   ├── event.DomainEvent       (unchanged)
│   └── TourId                  (moved from booking.core.domain)
├── booking                     (TourBooking and its ports only — no GuideTour refs)
└── guide             (new bounded context)
    ├── core.domain
    │   ├── GuideTour
    │   ├── GuideTourId
    │   ├── GuideTourStatus
    │   ├── event.TourStarted
    │   └── exception.*
    ├── core.inport
    │   ├── command.StartTourCommand
    │   ├── result.StartTourResult
    │   └── usecase.StartTourUseCase
    ├── core.outport.GuideTourRepository
    ├── inbound.driver.StartTourDriver
    ├── inbound.rest
    │   ├── GuideExceptionHandler (new — replaces GuideTour handlers in BookingExceptionHandler)
    │   ├── GuideTourController
    │   ├── GuideTourRestAPI
    │   ├── request.StartTourRequest
    │   └── response.StartTourResponse
    └── outbound.persistence.write
        ├── GuideTourJooqRepository
        └── GuideTourMapper
```

---

## Inventory of Changes

### Move and re-package (package declaration + imports only)

| Old location | New location |
|---|---|
| `booking.core.domain.TourId` | `shared.domain.TourId` |
| `booking.core.domain.GuideTour` | `guide.core.domain.GuideTour` |
| `booking.core.domain.GuideTourId` | `guide.core.domain.GuideTourId` |
| `booking.core.domain.GuideTourStatus` | `guide.core.domain.GuideTourStatus` |
| `booking.core.domain.event.TourStarted` | `guide.core.domain.event.TourStarted` |
| `booking.core.domain.exception.GuideTourNotFoundException` | `guide.core.domain.exception.GuideTourNotFoundException` |
| `booking.core.domain.exception.InvalidGuideTourStateException` | `guide.core.domain.exception.InvalidGuideTourStateException` |
| `booking.core.domain.exception.TourStartTooEarlyException` | `guide.core.domain.exception.TourStartTooEarlyException` |
| `booking.core.outport.GuideTourRepository` | `guide.core.outport.GuideTourRepository` |
| `booking.core.inport.command.StartTourCommand` | `guide.core.inport.command.StartTourCommand` |
| `booking.core.inport.result.StartTourResult` | `guide.core.inport.result.StartTourResult` |
| `booking.core.inport.usecase.StartTourUseCase` | `guide.core.inport.usecase.StartTourUseCase` |
| `booking.inbound.driver.StartTourDriver` | `guide.inbound.driver.StartTourDriver` |
| `booking.inbound.rest.GuideTourController` | `guide.inbound.rest.GuideTourController` |
| `booking.inbound.rest.GuideTourRestAPI` | `guide.inbound.rest.GuideTourRestAPI` |
| `booking.inbound.rest.request.StartTourRequest` | `guide.inbound.rest.request.StartTourRequest` |
| `booking.inbound.rest.response.StartTourResponse` | `guide.inbound.rest.response.StartTourResponse` |
| `booking.outbound.persistence.write.GuideTourJooqRepository` | `guide.outbound.persistence.write.GuideTourJooqRepository` |
| `booking.outbound.persistence.write.GuideTourMapper` | `guide.outbound.persistence.write.GuideTourMapper` |

### Tests (same moves)

| Old location | New location |
|---|---|
| `booking.core.domain.GuideTourTest` | `guide.core.domain.GuideTourTest` |
| `booking.inbound.rest.GuideTourControllerTest` | `guide.inbound.rest.GuideTourControllerTest` |
| `booking.outbound.persistence.write.GuideTourJooqRepositoryIT` | `guide.outbound.persistence.write.GuideTourJooqRepositoryIT` |

### New files

| File | Purpose |
|---|---|
| `guide.inbound.rest.GuideExceptionHandler` | Exception handler for `GuideTour*` exceptions, extracted from `BookingExceptionHandler` |
| `bootstrap.GuideConfig` | Wiring config for `guide` context (ClockPort and DomainEventPublisher are already beans — no new adapters needed) |

### Modified files

| File | Change |
|---|---|
| `booking.core.domain.TourBooking` | Update `TourId` import → `shared.domain.TourId` |
| `booking.core.domain.event.TourBookingRequested` | Update `TourId` import |
| `booking.core.outport.AvailabilityChecker` | Update `TourId` import |
| `booking.core.inport.command.RequestTourBookingCommand` | No `TourId` reference (uses String) — unchanged |
| `booking.outbound.persistence.write.TourBookingMapper` | Update `TourId` import |
| `booking.inbound.rest.BookingExceptionHandler` | Remove `GuideTour*` exception handlers; update SDD Javadoc |
| All `booking.*` files that import `booking.core.domain.TourId` | Update import |
| All moved files | Update `package` declaration and all imports |

---

## Risks

- **`TourId` is used in 5+ places in `booking`**: all must be updated atomically. A compilation
  failure will catch any missed update.
- **`GuideTourJooqRepository` uses jOOQ generated types** (`GuideTourRecord`): the generated
  package (`com.dominikgaller.alpinebooking.jooq.*`) is unaffected — only the repository
  wrapper moves.
- **`GuideTourControllerTest` uses `@SpringBootTest(classes = AlpineBookingApplication.class)`**:
  this works as long as `AlpineBookingApplication` scan covers the new `guide` package,
  which it does (`scanBasePackages = "com.dominikgaller.alpinebooking"`).

---

## Implementation Steps

1. Move `TourId` to `shared.domain` — update all imports in `booking` files
2. Create all `guide` domain files (re-package `GuideTour`, `GuideTourId`, `GuideTourStatus`, exceptions, `TourStarted`)
3. Create `guide` inport files (`StartTourCommand`, `StartTourResult`, `StartTourUseCase`)
4. Create `guide` outport file (`GuideTourRepository`)
5. Create `guide` driver (`StartTourDriver`)
6. Create `guide` REST layer (`GuideTourRestAPI`, `GuideTourController`, DTOs, `GuideExceptionHandler`)
7. Create `guide` persistence layer (`GuideTourMapper`, `GuideTourJooqRepository`)
8. Create `bootstrap.GuideConfig`
9. Migrate tests to `guide.*` packages
10. Clean up `booking`: remove old files, strip `GuideTour*` handlers from `BookingExceptionHandler`
11. Run `./gradlew clean test` — full suite green
12. Run `./gradlew build`

---

## Acceptance Criteria

- `booking.*` contains zero references to `GuideTour`, `GuideTourId`, `GuideTourStatus`, `TourStarted`, or `guide.*`
- `guide.*` contains zero references to `booking.*` (only `shared.*` allowed)
- All existing tests pass unchanged (only package declarations differ)
- `./gradlew clean test` and `./gradlew build` succeed
