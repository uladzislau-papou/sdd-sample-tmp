# Outbound Port Specification – TourBookingRepository

## Purpose

The `booking` context's write-side persistence contract for the `TourBooking` aggregate.

Declared in `booking.core.outport`, implemented in `booking.outbound.persistence`. The
core names what it needs; the adapter decides how. `DependencyRulesTest` enforces the
direction, and `ClassRoleRulesTest.repositoryOutportsExposeNoPersistenceType` enforces that
the contract stays in the core's vocabulary — a port returning an entity or a `Page` has
already leaked the adapter it was meant to hide.


## 1. Interface

```kotlin
interface TourBookingRepository {
    fun save(booking: TourBooking)
    fun findById(bookingId: BookingId): TourBooking?
    fun findConfirmedByTourId(tourId: TourId): List<TourBooking>
    fun update(booking: TourBooking)
}
```

Absence is a nullable return, not `Optional` (`coding-style.definition.md` § 1.4).


## 2. Operations

### 2.1 save

**Responsibility:** Persist a newly created aggregate.

**Preconditions:** no row exists for that identity; called inside the driver's transaction.

**Postconditions:** every field of the aggregate is written.

Used by: UC01.

### 2.2 findById

**Responsibility:** Load one aggregate by identity.

**Postconditions:** returns a reconstituted aggregate with **no pending events**, or `null`
when no row matches. Reconstitution re-checks no invariants — the row was valid when
written.

Used by: UC02, UC06.

### 2.3 update

**Responsibility:** Persist state changes to an existing aggregate.

**Postconditions:** **every mutable field is written** — today `status`,
`participant_count` and `available_capacity`. Immutable fields (`id`, `tour_id`,
`tour_date`, contact) are set once by `save` and must not change.

> **This contract is load-bearing, and it is stated as a standing obligation rather than a
> field list.** The original implementation wrote only `status`, so an entire use case's
> effect on the participant count was silently discarded — while its own tests stayed
> green, because each asserted only the field it cared about. **Any new mutable field on
> the aggregate must be written by `update` and asserted in the same increment.**
>
> The current adapter closes this defect *by construction* rather than by discipline: JPA's
> merge writes the whole entity, so the update cannot be partially forgotten. That is a
> property of this adapter, not of the port — the obligation stays written down here,
> because the next adapter may be hand-written SQL again. It is pinned by
> `TourBookingJpaRepositoryIT.update_writesEveryMutableField_notOnlyTheOneTheUseCaseChanged`,
> which asserts every field after changing one, precisely because asserting one field per
> test is what let the original defect through.

**Idempotency:** idempotent for identical aggregate state. It is **not** a no-op guard —
the aggregate decides whether a transition happens. UC06's driver relies on that: when
`markActive` no-ops on an already-active booking, no event is recorded, and the driver
skips `update` entirely.

Used by: UC02, UC06.

### 2.4 findConfirmedByTourId

**Responsibility:** Return the bookings for a tour that are eligible for activation.

**Postconditions:** every booking for that tour whose status is `CONFIRMED`, reconstituted
with no pending events. Empty list when none match — never null.

The filter is on `CONFIRMED` specifically, not "not cancelled": a `REQUESTED` booking that
nobody confirmed must not be activated by a tour starting (see I-06 in the aggregate spec).

Used by: UC06, from `TourStartedListener`.


## 3. Test Requirements

| Operation | Test |
|-----------|------|
| save + findById roundtrip | `TourBookingJpaRepositoryIT.save_thenFindById_roundTripsEveryField` |
| findById on a missing row | `TourBookingJpaRepositoryIT.findById_returnsNull_whenNoBookingHasThatIdentity` |
| update persists a transition | `TourBookingJpaRepositoryIT.update_persistsTheStatusTransition` |
| update writes every mutable field | `TourBookingJpaRepositoryIT.update_writesEveryMutableField_notOnlyTheOneTheUseCaseChanged` |

`findConfirmedByTourId` is exercised at the use-case level by
`TourStartedListenerTest.onTourStarted_activatesEveryConfirmedBookingForThatTour` and
`TourStartedListenerTest.onTourStarted_doesNothing_whenNoBookingIsConfirmed`. **It has no
integration test of its own, and that is a gap, not a decision** — the status filter is
exactly the kind of predicate that behaves differently against a real database than against
a mock. A service adopting this template should close it.

These integration tests run against PostgreSQL via Testcontainers, in the `integrationTest`
Gradle task. They also prove the Flyway migrations and the JPA mapping agree, since the
schema comes from the migrations and `ddl-auto` is `validate`.
