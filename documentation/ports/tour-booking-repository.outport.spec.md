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

Used by: UC02, UC03, UC04, UC06, UC07.

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

> **This contract is load-bearing — a standing obligation, not a field list.** The
> implementation previously wrote only `status`, so UC04's participant-count and
> capacity changes were silently discarded. Two integration tests now pin it —
> `TourBookingJooqRepositoryIT.update_changesParticipantCount_inDatabase` and
> `.update_changesAvailableCapacity_inDatabase`. **Any new mutable field on the
> aggregate must be added to the `update` statement *and* to an
> `IT.update_changes*_inDatabase` assertion in the same increment**, or it will not
> survive a write. UC07 conformed without extending it: `markCompleted` mutates only
> `status`, and `completedAt` is event payload, not aggregate state. The guide-side
> port carries the same obligation
> (`ports/guide-tour-repository.outport.spec.md` § update) — this port is where the
> original defect occurred.

**Exceptions:** `IllegalStateException` if the update did not affect exactly one row.
Updating a booking that no longer exists fails loudly rather than silently no-opping.

**Idempotency:** Idempotent for identical aggregate state — the same `update` applied
twice produces the same row. It is not a no-op guard: the aggregate decides whether a
transition should happen (e.g. `markActive` returns early when already ACTIVE, so the
driver never calls `update`).

Used by: UC02, UC03, UC04, UC06, UC07.

### 2.4 findConfirmedByTourId

```
List<TourBooking> findConfirmedByTourId(TourId tourId)
```

**Responsibility:** Return the bookings for a tour that are eligible for activation.

**Postconditions:** an empty list when none qualify, never null; each element fully
reconstituted with no pending events; no ordering guaranteed.

Used by: UC06 (`TourStartedListener`).

> **Why the criterion lives here.** A `status() == CONFIRMED` filter in the listener is
> business logic in an inbound adapter (`architecture.definition.md` § 4.8). It is also
> load-bearing: the listener runs one `REQUIRES_NEW` transaction for the whole fan-out and
> `markActive` throws for CANCELLED or COMPLETED, so removing the filter and relying on
> the aggregate guard would let one ineligible booking roll back the entire batch. Making
> it a query keeps the adapter free of conditionals without weakening the domain guard.

### 2.5 findActiveByTourId

```
List<TourBooking> findActiveByTourId(TourId tourId)
```

**Responsibility:** Return the bookings for a tour that are eligible for completion.

**Postconditions:** an empty list when none qualify, never null; each element fully
reconstituted with no pending events; no ordering guaranteed.

Used by: UC07 (`TourCompletedListener`).

> Mirrors § 2.4 for the completion side, for the same two reasons: a status filter in
> `TourCompletedListener` would be business logic in an inbound adapter
> (`architecture.definition.md` § 4.8), and `markCompleted` throws for REQUESTED,
> CONFIRMED and CANCELLED, so relying on the aggregate guard alone would let one
> ineligible booking roll back the entire `REQUIRES_NEW` fan-out. The jOOQ predicate
> (`tour_id` **and** `status = 'ACTIVE'`) is pinned by
> `TourBookingJooqRepositoryIT.findActiveByTourId_returnsOnlyActiveBookingsForThatTour`
> and `.findActiveByTourId_returnsEmpty_whenNoActiveBookingsExist` — the listener's
> stub cannot catch a wrong column or literal in the generated SQL.

### ~~2.6 findByTourId~~ — removed

`List<TourBooking> findByTourId(TourId tourId)` returned every booking for a tour
regardless of status. Its only caller was `TourStartedListener`, which moved to
`findConfirmedByTourId` so the status criterion would not sit in an inbound adapter
(§ 2.4). That left it with **no caller and no test**.

Removed rather than kept "in case". An unused method on a port is a liability: it has to
be implemented by every adapter and every test stub — five stubs in this codebase — and
it invites a future caller to load aggregates it does not need. `git` preserves it if a
genuine caller appears, and by then the right shape may well be a read-side projection
(`architecture.definition.md` § 4.6) rather than a write-side aggregate load.


## 3. Transaction Boundary

The transaction is owned by the calling driver, never by the repository. The
implementation MUST NOT start its own transaction; it participates in the active one
(`architecture.definition.md` § 4.4, § 4.6).

Owners: `RequestTourBookingDriver`, `ConfirmTourBookingDriver`,
`CancelTourBookingDriver`, `ChangeParticipantsDriver`, `MarkBookingActiveDriver`,
`MarkBookingCompletedDriver`.

`TourStartedListener` and `TourCompletedListener` are a special case: each runs
`AFTER_COMMIT` of the guide transaction in a **new** transaction (`REQUIRES_NEW`),
per ADR-0002. The drivers they call join that transaction.

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
