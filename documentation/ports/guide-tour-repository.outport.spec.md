# Outbound Port Specification – GuideTourRepository

## Purpose

The `guide` context's write-side persistence contract for the `GuideTour` aggregate.

Declared in `guide.core.outport`, implemented in `guide.outbound.persistence`. Mirrors
`tour-booking-repository.outport.spec.md` in shape and obligations — deliberately, since a
second context inventing its own persistence idiom is how "every context follows the same
ontology" (`architecture.definition.md` § 11 rule 5) stops being true.


## 1. Interface

```kotlin
interface GuideTourRepository {
    fun save(guideTour: GuideTour)
    fun findById(guideTourId: GuideTourId): GuideTour?
    fun update(guideTour: GuideTour)
}
```

No query method: nothing in this context fans out over guide tours. The booking side has
`findConfirmedByTourId` because UC06 activates many bookings for one tour; the reverse
relationship does not exist.


## 2. Operations

### 2.1 save

Persists a newly scheduled tour. Every field is written, including a null `started_at` — a
tour that has not started must stay distinguishable from one that started at the epoch.

Used by: fixtures and integration tests. **The example has no use case that schedules a
tour**; a tour arrives in the database already scheduled. That is a boundary of the
example, recorded here rather than left to be discovered.

### 2.2 findById

Loads one aggregate by identity, reconstituted with no pending events, or `null` when no row
matches.

Used by: UC05.

### 2.3 update

Persists state changes. **Every mutable field is written** — today `status` and
`started_at` — for the reason recorded in the booking-side port § 2.3, where the original
defect occurred. The current adapter closes it by construction, since JPA's merge writes
the whole entity.

Used by: UC05.


## 3. Timestamp handling

`Instant` is converted to and from `LocalDateTime` at **UTC** by `GuideTourMapper`, because
the columns are `TIMESTAMP` without a zone. Storing local time would make the stored value
depend on the server's timezone, turning a deployment detail into data corruption. The
booking side follows the same rule.

A null `started_at` maps to a null `startedAt` and back. This is asserted separately,
because a careless default in the conversion would silently invent a start time and satisfy
the aggregate's I-04 in letter while breaking it in fact.


## 4. Test Requirements

| Operation | Test |
|-----------|------|
| save + findById roundtrip, including UTC conversion | `GuideTourJpaRepositoryIT.save_thenFindById_roundTripsEveryField` |
| findById on a missing row | `GuideTourJpaRepositoryIT.findById_returnsNull_whenNoTourHasThatIdentity` |
| update persists the transition and the start time | `GuideTourJpaRepositoryIT.update_persistsTheTransition_andTheStartTime` |
| a never-started tour keeps a null start time | `GuideTourJpaRepositoryIT.startedAt_staysNull_forATourThatWasNeverStarted` |

Run against PostgreSQL via Testcontainers, in the `integrationTest` Gradle task.
