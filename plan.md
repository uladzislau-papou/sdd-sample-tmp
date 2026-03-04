# Plan – UC03 Cancel Tour Booking

## Status
READY TO IMPLEMENT

---

## 1. Spec Gaps & Clarifications

The existing `uc03-cancle-tour-booking.spec.md` is intentionally thin (SPECIFIED state).
Before implementation, the following gaps must be addressed:

### 1.1 Valid Source States for Cancellation
From `TourBookingStatus` JavaDoc the allowed transitions are:
- **REQUESTED → CANCELLED** ✓
- **CONFIRMED → CANCELLED** ✓
- ACTIVE → CANCELLED ✗ (not listed as valid)
- COMPLETED → CANCELLED ✗ (not listed as valid)

**Decision:** `cancel()` throws `InvalidBookingStateException` if status is neither REQUESTED nor CONFIRMED.

### 1.2 REST Endpoint
UC02 uses `POST /…/confirm`. UC03 could use `POST /…/cancel` (consistent with action-verb pattern) or
`DELETE /api/v1/bookings/{bookingId}` (idiomatic REST for removing/voiding a resource).

**Decision:** Use `DELETE /api/v1/bookings/{bookingId}` — semantically accurate, RESTful,
and avoids adding a verb-suffix endpoint.

### 1.3 HTTP Response
Consistent with UC02: `200 OK` with `{ "status": "CANCELLED" }`.
No request body. Path variable: `bookingId` (UUID string).

### 1.4 Missing ADRs
No new ADR required. UC03 follows the exact same patterns as UC02:
- same load → guard → transition → update → publish flow
- same transaction boundary (`@Transactional` on driver)
- same event publication after commit (ADR-0002)
- no new packages, dependencies, or persistence changes

---

## 2. Spec to Update

**File:** `documentation/use-cases/uc03-cancle-tour-booking.spec.md`

Enrich with:
- Status → IMPLEMENTED (after implementation)
- Valid source states: REQUESTED, CONFIRMED
- REST endpoint definition: `DELETE /api/v1/bookings/{bookingId}`
- HTTP success response: `200 OK` with `{ "status": "CANCELLED" }`
- Full error table (404, 409)
- Detailed flow (with class references)
- Detailed acceptance criteria (REQUESTED and CONFIRMED happy paths)
- Failure scenarios table
- Full test requirements

---

## 3. Implementation Steps

### Step 1 – Domain Event
**File:** `core/domain/event/TourBookingCancelled.java`

New record implementing `DomainEvent`:
```
record TourBookingCancelled(BookingId bookingId, Instant cancelledAt) implements DomainEvent {}
```

### Step 2 – Domain Method `TourBooking.cancel(Instant now)`
**File:** `core/domain/TourBooking.java`

Add method:
- Guard: `if (status != REQUESTED && status != CONFIRMED)` → throw `InvalidBookingStateException(status)`
- Transition: `status = CANCELLED`
- Record event: `domainEvents.add(new TourBookingCancelled(bookingId, now))`

Also update the class-level Javadoc to mention `cancel`.

### Step 3 – Inport: Command, Result, Use Case Interface
**Files:**
- `core/inport/CancelTourBookingCommand.java` — `record(String bookingId)`
- `core/inport/CancelTourBookingResult.java` — `record(String status)`
- `core/inport/CancelTourBookingUseCase.java` — `interface { CancelTourBookingResult cancel(CancelTourBookingCommand) }`

### Step 4 – Driver: `CancelTourBookingDriver`
**File:** `inbound/driver/CancelTourBookingDriver.java`

`@Service @Transactional` class implementing `CancelTourBookingUseCase`:
1. Parse `BookingId` from `command.bookingId()` via `UUID.fromString`
2. `Instant now = clockPort.now()`
3. `tourBookingRepository.findById(bookingId)` → throw `BookingNotFoundException` if empty
4. `booking.cancel(now)`
5. `tourBookingRepository.update(booking)`
6. `booking.pullDomainEvents().forEach(domainEventPublisher::publish)`
7. Return `new CancelTourBookingResult(booking.status().name())`

Injects: `TourBookingRepository`, `DomainEventPublisher`, `ClockPort`

### Step 5 – REST Response DTO
**File:** `inbound/rest/CancelTourBookingResponse.java` — `record(String status)`

### Step 6 – REST Controller
**File:** `inbound/rest/TourBookingController.java` — add:
- Inject `CancelTourBookingUseCase`
- New handler:
  ```
  DELETE /{bookingId}
  → CancelTourBookingCommand → cancelUseCase.cancel() → 200 OK + CancelTourBookingResponse
  ```

No changes needed to `BookingExceptionHandler` — `BookingNotFoundException` (404) and
`InvalidBookingStateException` (409) are already mapped.

### Step 7 – Bootstrap (if needed)
`CancelTourBookingDriver` is annotated `@Service` so Spring picks it up automatically via
component scanning. No explicit bean declaration in `BookingConfig` needed.

### Step 8 – Tests

#### 8a. Domain Test
**File:** `TourBookingTest.java` (extend existing)

New test cases:
- `cancel_fromRequested_transitionsToCancelled()` — happy path
- `cancel_fromConfirmed_transitionsToCancelled()` — happy path
- `cancel_fromActive_throwsInvalidBookingStateException()` — guard
- `cancel_fromCompleted_throwsInvalidBookingStateException()` — guard
- `cancel_publishesTourBookingCancelledEvent()` — event verification

#### 8b. Driver Test
**File:** `inbound/driver/CancelTourBookingDriverTest.java`

Stub-based unit test (no Spring):
- Happy path: REQUESTED booking → result.status() == "CANCELLED", event published
- Happy path: CONFIRMED booking → same
- Not-found guard: empty repository → `BookingNotFoundException` thrown
- Invalid state guard: ACTIVE booking → `InvalidBookingStateException` propagated

Use `booking.pullDomainEvents()` after pre-loading stub repo to clear pending events
(same pattern as `ConfirmTourBookingDriverTest`).

#### 8c. Controller / Web Test
**File:** `TourBookingControllerTest.java` (extend existing or add cases)

- `DELETE /api/v1/bookings/{id}` → 200 with `{ "status": "CANCELLED" }`
- Unknown ID → 404 `{ "error": "…" }`
- Invalid state → 409 `{ "error": "…" }`

#### 8d. Persistence Integration Test
**File:** `TourBookingJooqRepositoryIT.java` (extend existing)

- Save a booking → `cancel()` → `update()` → `findById()` → assert status == CANCELLED

No new repository methods; `update()` already persists the full status field.

### Step 9 – REST Client File
**File:** `rest/uc03-cancel-tour-booking.http`

Cover:
- Happy path: `DELETE /api/v1/bookings/{{bookingId}}`
- Error: booking not found → 404
- Error: invalid state (e.g. already CANCELLED or ACTIVE) → 409

### Step 10 – Update Spec File
Update `uc03-cancle-tour-booking.spec.md` status from `SPECIFIED` to `IMPLEMENTED`
and fill in all missing sections per the decisions above.

---

## 4. Acceptance Criteria

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Cancel a REQUESTED booking | 200 OK `{ "status": "CANCELLED" }` |
| 2 | Cancel a CONFIRMED booking | 200 OK `{ "status": "CANCELLED" }` |
| 3 | Cancel with unknown bookingId | 404 `{ "error": "…" }` |
| 4 | Cancel ACTIVE booking | 409 `{ "error": "…" }` |
| 5 | Cancel COMPLETED booking | 409 `{ "error": "…" }` |
| 6 | `TourBookingCancelled` event published after cancel | Event captured in driver test |
| 7 | Status persisted as CANCELLED | Persistence IT round-trip confirms |

---

## 5. Risks & Notes

| Risk | Mitigation |
|------|------------|
| `cancel()` must guard BOTH ACTIVE and COMPLETED, not just one | Assert both in domain test |
| `InvalidBookingStateException` already handles any non-allowed state | Re-use existing exception, no new type needed |
| HTTP method choice (DELETE vs POST /cancel) | Decision: DELETE — no impact on existing tests |
| Already-CANCELLED booking cancellation | CANCELLED is not REQUESTED or CONFIRMED → guard fires → 409 (correct) |

---

## 6. File Change Summary

| Action | File |
|--------|------|
| NEW    | `core/domain/event/TourBookingCancelled.java` |
| MODIFY | `core/domain/TourBooking.java` |
| NEW    | `core/inport/CancelTourBookingCommand.java` |
| NEW    | `core/inport/CancelTourBookingResult.java` |
| NEW    | `core/inport/CancelTourBookingUseCase.java` |
| NEW    | `inbound/driver/CancelTourBookingDriver.java` |
| NEW    | `inbound/rest/CancelTourBookingResponse.java` |
| MODIFY | `inbound/rest/TourBookingController.java` |
| NEW    | `rest/uc03-cancel-tour-booking.http` |
| MODIFY | `documentation/use-cases/uc03-cancle-tour-booking.spec.md` |
| MODIFY | `TourBookingTest.java` (domain tests) |
| NEW    | `CancelTourBookingDriverTest.java` |
| MODIFY | `TourBookingControllerTest.java` |
| MODIFY | `TourBookingJooqRepositoryIT.java` |
