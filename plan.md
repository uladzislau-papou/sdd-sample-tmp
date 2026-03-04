# Plan: UC02 – Confirm Tour Booking

## Status
PLANNED

---

## 1. Spec Analysis

### 1.1 UC02 Spec Gaps

The existing spec at `documentation/use-cases/uc02-confirm-tour-booking.spec.md` is underspecified. The following decisions fill the gaps:

| Gap | Decision |
|-----|----------|
| REST endpoint | `POST /api/v1/bookings/{bookingId}/confirm` |
| Success HTTP status | `200 OK` |
| Success response body | `{ "status": "CONFIRMED" }` |
| NotFound HTTP status | `404 Not Found` |
| InvalidState HTTP status | `409 Conflict` |
| Error response body | `{ "error": "<message>" }` (matches existing pattern) |
| bookingId validation | UUID format, non-blank path variable |

**Rationale for `POST /{bookingId}/confirm`:** State transitions on resources are cleanly expressed as POST to a sub-resource action URI. This avoids a generic PATCH that exposes status as a free-form field. Consistent with common REST practice (GitHub, Stripe, etc.).

### 1.2 Missing Port Spec Additions

`TourBookingRepository` (currently save-only) needs two new operations for UC02:
- `findById(BookingId) → Optional<TourBooking>` — load aggregate from DB
- `update(TourBooking)` — persist status change

`DomainEventPublisher` currently types its method to `TourBookingRequested`. UC02 introduces a second event (`TourBookingConfirmed`), requiring generalization.

---

## 2. Cross-Cutting Change: DomainEvent Generalization

### Problem
`TourBooking.pullDomainEvents()` returns `List<TourBookingRequested>` (UC01-specific).
`DomainEventPublisher.publish()` accepts only `TourBookingRequested`.

With UC02, the aggregate must emit `TourBookingConfirmed` via the same mechanism.

### Solution
Introduce a `DomainEvent` marker interface in `core.domain.event`:

```java
package com.dominikgaller.alpinebooking.booking.core.domain.event;
public interface DomainEvent {}
```

Both `TourBookingRequested` and `TourBookingConfirmed` implement `DomainEvent`.
`TourBooking.domainEvents` becomes `List<DomainEvent>`.
`DomainEventPublisher.publish()` becomes `void publish(DomainEvent event)`.

### Files affected by this change
- `core.domain.event.TourBookingRequested` — implements `DomainEvent`
- `core.domain.TourBooking` — `domainEvents` list + `pullDomainEvents()` return type
- `core.outport.DomainEventPublisher` — method signature
- `outbound.integration.LoggingDomainEventPublisher` — implementation
- `inbound.driver.RequestTourBookingDriver` — no functional change (still compiles)
- `inbound.driver.RequestTourBookingDriverTest` — mock type update

No ADR required: this is an internal port refinement, not an architectural decision. The event publication strategy (ADR-0002) is unchanged.

---

## 3. Aggregate Reconstitution

### Problem
`TourBooking` has a private constructor. The persistence adapter cannot reconstruct the aggregate from a DB record without a dedicated factory.

### Solution
Add a package-private static factory `TourBooking.reconstitute(...)` that accepts all fields (including `status`) without applying creation-time invariants (future date, capacity check). Reconstitution is loading an already-valid past fact, not creating a new booking.

```java
static TourBooking reconstitute(BookingId, TourId, TourDate, ParticipantCount,
                                 AvailableCapacity, ParticipantContact, TourBookingStatus)
```

`TourBookingMapper` (same package as the repository) calls this method.

---

## 4. Required New Artifacts

### Domain Layer (`core.domain`)
| Artifact | Type | Description |
|----------|------|-------------|
| `event.DomainEvent` | Interface | Marker interface for all domain events |
| `event.TourBookingConfirmed` | Record | Emitted after REQUESTED → CONFIRMED |
| `TourBooking.confirm()` | Method | Transitions status to CONFIRMED, records `TourBookingConfirmed` |
| `TourBooking.reconstitute()` | Static factory | Loads aggregate from persistence without re-validating creation rules |
| `exception.InvalidBookingStateException` | Class | Thrown by `confirm()` if state ≠ REQUESTED; maps to HTTP 409 |
| `exception.BookingNotFoundException` | Class | Thrown by driver when `findById` returns empty; maps to HTTP 404 |

**`TourBookingConfirmed` record fields:**
- `bookingId: BookingId`
- `occurredAt: Instant`

**`confirm()` invariant:** Throws `InvalidBookingStateException` if `this.status != REQUESTED`.

### Port Layer
| Artifact | Package | Description |
|----------|---------|-------------|
| `TourBookingRepository` extension | `core.outport` | Add `findById(BookingId): Optional<TourBooking>` + `update(TourBooking)` |
| `DomainEventPublisher` change | `core.outport` | `publish(DomainEvent)` replaces `publish(TourBookingRequested)` |
| `ConfirmTourBookingCommand` | `core.inport` | Record: `String bookingId` |
| `ConfirmTourBookingResult` | `core.inport` | Record: `String status` |
| `ConfirmTourBookingUseCase` | `core.inport` | Interface: `ConfirmTourBookingResult confirm(ConfirmTourBookingCommand)` |

### Persistence Adapter (`outbound.persistence.write`)
| Artifact | Description |
|----------|-------------|
| `TourBookingMapper.toDomain(TourBookingRecord)` | Reverse mapping using `TourBooking.reconstitute()` |
| `TourBookingJooqRepository.findById(BookingId)` | SELECT by PK, map to domain |
| `TourBookingJooqRepository.update(TourBooking)` | UPDATE `status` column by PK |

The `update()` call issues `UPDATE tour_booking SET status = ? WHERE id = ?`. Only `status` is mutable.

### Application Driver (`inbound.driver`)
| Artifact | Description |
|----------|-------------|
| `ConfirmTourBookingDriver` | `@Service @Transactional` implementing `ConfirmTourBookingUseCase` |

**Flow:**
1. Parse `BookingId` from command
2. `tourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
3. `booking.confirm()` → throws `InvalidBookingStateException` if wrong state
4. `tourBookingRepository.update(booking)`
5. `booking.pullDomainEvents().forEach(domainEventPublisher::publish)`
6. Return `ConfirmTourBookingResult(booking.status().name())`

### REST Layer (`inbound.rest`)
| Artifact | Description |
|----------|-------------|
| `ConfirmTourBookingResponse` | Record: `String status` |
| `TourBookingController` | Add `POST /api/v1/bookings/{bookingId}/confirm` → 200 |
| `BookingExceptionHandler` | Add handlers: `BookingNotFoundException` → 404, `InvalidBookingStateException` → 409 |
| `rest/uc02-confirm-tour-booking.http` | Happy path, 404 case, 409 case |

---

## 5. Implementation Steps

### Step 1 – Update UC02 Spec
- Enrich `documentation/use-cases/uc02-confirm-tour-booking.spec.md` with HTTP codes, endpoint, response format, exception names

### Step 2 – Domain Layer
1. Create `core.domain.event.DomainEvent` marker interface
2. Update `TourBookingRequested` to implement `DomainEvent`
3. Create `core.domain.event.TourBookingConfirmed` (record)
4. Create `core.domain.exception.InvalidBookingStateException`
5. Create `core.domain.exception.BookingNotFoundException`
6. Add `TourBooking.reconstitute()` static factory
7. Add `TourBooking.confirm()` method
8. Change `TourBooking.domainEvents` to `List<DomainEvent>` and update `pullDomainEvents()`

### Step 3 – Port Layer
1. Update `DomainEventPublisher` to `publish(DomainEvent)`
2. Add `findById` and `update` to `TourBookingRepository`
3. Create `ConfirmTourBookingCommand`, `ConfirmTourBookingResult`, `ConfirmTourBookingUseCase`

### Step 4 – Adapter: fix compilation
1. Update `LoggingDomainEventPublisher` to accept `DomainEvent`
2. Update `TourBookingMapper.toRecord()` — no change needed
3. Add `TourBookingMapper.toDomain(TourBookingRecord)`
4. Add `TourBookingJooqRepository.findById()` and `update()`

### Step 5 – Application Driver
1. Create `ConfirmTourBookingDriver` with full UC02 flow

### Step 6 – REST Layer
1. Create `ConfirmTourBookingResponse`
2. Add `POST /{bookingId}/confirm` endpoint to `TourBookingController`
3. Add exception handlers in `BookingExceptionHandler`
4. Create `rest/uc02-confirm-tour-booking.http`

### Step 7 – Tests
1. `TourBookingTest`: `confirm()` happy path + invalid state guard
2. `ConfirmTourBookingDriverTest`: happy path, not-found, invalid-state, event published, persistence called
3. `TourBookingControllerTest`: 200 OK, 404, 409
4. `TourBookingJooqRepositoryIT`: `findById` after `save`, `update` changes status

### Step 8 – Bootstrap Wiring
- `BookingConfig` — `ConfirmTourBookingDriver` is `@Service` and auto-detected; no explicit bean needed (same as `RequestTourBookingDriver`)

### Step 9 – Verify & Close
1. `./gradlew clean test` — must pass
2. `./gradlew build` — must pass
3. Mark UC02 spec as `IMPLEMENTED`

---

## 6. Affected Files (complete list)

**Modified:**
- `documentation/use-cases/uc02-confirm-tour-booking.spec.md`
- `core.domain.event.TourBookingRequested` (implements DomainEvent)
- `core.domain.TourBooking` (confirm, reconstitute, pullDomainEvents generalization)
- `core.outport.TourBookingRepository` (findById, update)
- `core.outport.DomainEventPublisher` (publish(DomainEvent))
- `outbound.integration.LoggingDomainEventPublisher` (method signature)
- `outbound.persistence.write.TourBookingMapper` (toDomain)
- `outbound.persistence.write.TourBookingJooqRepository` (findById, update)
- `inbound.rest.TourBookingController` (new endpoint)
- `inbound.rest.BookingExceptionHandler` (new handlers)
- `inbound.driver.RequestTourBookingDriverTest` (mock type update)
- `outbound.persistence.write.TourBookingJooqRepositoryIT` (new test methods)
- `inbound.rest.TourBookingControllerTest` (new test methods)

**Created:**
- `core.domain.event.DomainEvent`
- `core.domain.event.TourBookingConfirmed`
- `core.domain.exception.InvalidBookingStateException`
- `core.domain.exception.BookingNotFoundException`
- `core.inport.ConfirmTourBookingCommand`
- `core.inport.ConfirmTourBookingResult`
- `core.inport.ConfirmTourBookingUseCase`
- `inbound.driver.ConfirmTourBookingDriver`
- `inbound.rest.ConfirmTourBookingResponse`
- `core.domain.TourBookingTest` additions (or extended)
- `inbound.driver.ConfirmTourBookingDriverTest`
- `rest/uc02-confirm-tour-booking.http`

---

## 7. Risks

| Risk | Mitigation |
|------|------------|
| `DomainEventPublisher` type change breaks `RequestTourBookingDriver` | Verify at compile time; change is backwards-compatible (covariant) |
| `reconstitute()` bypasses invariants | Document explicitly; only called by mapper in `outbound.persistence.write` (same package = visibility control) |
| Missing test coverage on `toDomain` roundtrip | IT test: `save` → `findById` → assert all fields equal |
| `update()` issues partial UPDATE — wrong ID | Assert `execute()` returns 1, throw if not |

---

## 8. Acceptance Criteria

- [ ] `POST /api/v1/bookings/{validId}/confirm` returns `200 OK` with `{ "status": "CONFIRMED" }`
- [ ] Unknown bookingId returns `404 Not Found` with `{ "error": "..." }`
- [ ] Booking in non-REQUESTED state returns `409 Conflict` with `{ "error": "..." }`
- [ ] `TourBookingConfirmed` domain event is published (logged) after transaction commit
- [ ] `./gradlew clean test` passes with zero failures
- [ ] `./gradlew build` succeeds
- [ ] `rest/uc02-confirm-tour-booking.http` covers all three cases
- [ ] UC02 spec status updated to `IMPLEMENTED`