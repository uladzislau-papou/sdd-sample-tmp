# Port Specification – TourBookingRepository (Outport)

## Purpose

Defines the persistence boundary for the `TourBooking` aggregate on the write side.
This outport abstracts the storage mechanism from the domain and application layer.

SDD: See `documentation/domain/aggregate-tour-booking.spec.md`

> This spec was written for UC01 and stated "Only `save` is required". Four more use
> cases have since been implemented and the interface grew to four methods, but the
> spec was never updated — so it documented a `save`-only port while five use cases
> depended on `findById`, `update` and `findByTourId`. Found by `spec-documenter`.


## 1. Interface

```
booking.core.outport.TourBookingRepository
```

Framework-free. Exposes only domain types plus `Optional` and `List`. No jOOQ record
or generated `com.dominikgaller.alpinebooking.jooq.*` type appears in any signature
(`architecture.definition.md` § 6 rule 6).


## 2. Method Contracts

### 2.1 save

```
void save(TourBooking booking)
```

**Responsibility:** Persist a new `TourBooking` aggregate atomically.

**Preconditions:**
- `booking` must be non-null and in a valid, always-valid state.
- No row may already exist for `booking.bookingId()`.

**Postconditions:**
- All fields of the aggregate are durably persisted.
- The operation is atomic — either all fields are written or none are.

**Exceptions:** a duplicate id surfaces as Spring's `DuplicateKeyException`, translated
from the H2 constraint violation. Pinned by
`TourBookingJooqRepositoryIT.save_duplicateId_throwsDuplicateKeyException`.

**Idempotency:** Not required. Saving a duplicate `bookingId` is a programming error —
`BookingId` is generated before `save` is called.

Used by: UC01 (`RequestTourBookingDriver`).

### 2.2 findById

```
Optional<TourBooking> findById(BookingId bookingId)
```

**Responsibility:** Load one aggregate by identity, fully reconstituted.

**Preconditions:** `bookingId` non-null.

**Postconditions:**
- `Optional.empty()` when no row matches — **not** an exception. Translating absence
  into `BookingNotFoundException` is the driver's job, because "not found" is a use
  case outcome (a 404) rather than a persistence failure.
- When present, rebuilt via `TourBooking.reconstitute(...)`, which deliberately skips
  creation-time invariant checks (`modelling.definition.md` § Rehydration Rule).
- The returned aggregate has **no pending domain events**.

Used by: UC02, UC03, UC04, UC06.

### 2.3 update

```
void update(TourBooking booking)
```

**Responsibility:** Persist state changes to an existing aggregate.

**Preconditions:**
- A row must already exist for `booking.bookingId()`.
- Called inside the driver's transaction.

**Postconditions:**
- **Every mutable field is written**: `status`, `participant_count` and
  `available_capacity`.
- Immutable fields (`id`, `tour_id`, `tour_date`, contact) are set once by `save` and
  must not change.

> **This contract is load-bearing.** The implementation previously wrote only
> `status`, so UC04's participant-count and capacity changes were silently discarded.
> Two integration tests now pin it —
> `TourBookingJooqRepositoryIT.update_changesParticipantCount_inDatabase` and
> `.update_changesAvailableCapacity_inDatabase`. **Any new mutable field on the
> aggregate must be added here and covered by an IT**, or it will not survive a write.

**Exceptions:** `IllegalStateException` if the update did not affect exactly one row.
Updating a booking that no longer exists fails loudly rather than silently no-opping.

**Idempotency:** Idempotent for identical aggregate state — the same `update` applied
twice produces the same row. It is not a no-op guard: the aggregate decides whether a
transition should happen (e.g. `markActive` returns early when already ACTIVE, so the
driver never calls `update`).

Used by: UC02, UC03, UC04, UC06.

### 2.4 findByTourId

```
List<TourBooking> findByTourId(TourId tourId)
```

**Responsibility:** Load every booking attached to a tour, as full aggregates.

**Preconditions:** `tourId` non-null.

**Postconditions:**
- An empty list when no booking references the tour — never null.
- Each element is fully reconstituted with no pending events.
- No ordering is guaranteed. Callers must not depend on one.

Used by: UC06 (`TourStartedListener`) to fan out the ACTIVE transition across every
booking for a started tour.

> **Write-side query.** This returns aggregates rather than a projection, which
> `architecture.definition.md` § 4.6 assigns to `outbound.persistence.read`. It sits
> on the write side because its caller mutates every aggregate it returns, so it must
> respect invariants. A read-only "bookings for a tour" listing would belong on the
> read side instead. Worth revisiting if this method acquires a query-only caller.


## 3. Transaction Boundary

The transaction is owned by the calling driver, never by the repository. The
implementation MUST NOT start its own transaction; it participates in the active one
(`architecture.definition.md` § 4.4, § 4.6).

Owners: `RequestTourBookingDriver`, `ConfirmTourBookingDriver`,
`CancelTourBookingDriver`, `ChangeParticipantsDriver`, `MarkBookingActiveDriver`.

`TourStartedListener` is a special case: it runs `AFTER_COMMIT` of the guide
transaction in a **new** transaction (`REQUIRES_NEW`), per ADR-0002. The drivers it
calls join that transaction.

Event publication is deferred to after commit by the `DomainEventPublisher` adapter,
so a rollback cannot leak an event for a change that never landed.


## 4. Reference Implementation

`booking.outbound.persistence.write.TourBookingJooqRepository`, with mapping in
`TourBookingMapper`.

Technology: jOOQ over H2. Schema managed by Flyway
(`V1__DDL_create_tour_booking.sql`).

Mapping notes:
- `BookingId` ↔ `VARCHAR(36)`; `TourId` ↔ `VARCHAR(255)`; `TourDate` ↔ `DATE`.
- `ParticipantContact` flattens to `contact_name` / `contact_email`.
- `TourBookingStatus` ↔ `VARCHAR(50)` via `name()`.
- There are no `started_at` or `guide_tour_id` columns, and correctly so: `TourBooking`
  holds no such fields. `markActive(Instant, String)` takes both purely as
  `BookingActivated` event payload.


## 5. Constraints

- MUST NOT expose jOOQ records, JPA entities, or any persistence type through this interface.
- MUST NOT load partial aggregates.
- Parameters and return types MUST be domain types only.
- MUST NOT contain business logic — status transitions belong to the aggregate.
- MUST NOT publish domain events.
- MUST NOT open or commit transactions.
- MUST return `Optional.empty()` / an empty list rather than throwing for absence.
- MAY depend on `core.domain` and `core.outport` only, never on `inbound.*`
  (`architecture.definition.md` § 6 rule 5).


## 6. Known Gaps

- **No optimistic locking.** Two concurrent updates to the same booking can both read
  the same state and both write; the second wins. No version column exists in
  `V1__DDL_create_tour_booking.sql`. Adding one is an ADR (persistence strategy,
  `sdd.playbook.md` § 6 item 4).
- **No `delete`.** Deliberate — bookings are cancelled, never removed.
- **`findByTourId` has no test.** Its only caller is `TourStartedListener`, which is
  itself untested (`uc06-mark-booking-active.spec.md` § 10, `tasks.md` block 2.3).
