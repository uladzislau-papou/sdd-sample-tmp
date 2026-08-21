# Port Specification – GuideTourRepository (Outport)

## Purpose

Write-side persistence boundary for the `GuideTour` aggregate.

The `guide` core owns this abstraction; `outbound.persistence.write` provides the
implementation (`architecture.definition.md` § 4.3, § 4.6). The core never learns
that persistence is jOOQ over H2.

SDD: `documentation/domain/aggregate-guide-tour.spec.md`,
`documentation/use-cases/uc05-start-tour.spec.md`,
`documentation/use-cases/uc11-complete-tour.spec.md`.


## 1. Interface

```
guide.core.outport.GuideTourRepository
```

Framework-free. Exposes only domain types — `GuideTour`, `GuideTourId`,
`java.util.Optional`. No jOOQ record or generated type appears in any signature
(`architecture.definition.md` § 6 rule 6).


## 2. Method Contracts

### 2.1 save

```
void save(GuideTour guideTour)
```

**Responsibility:** Insert a new guide tour.

**Preconditions:**
- `guideTour` must be non-null and valid (Always-Valid — it cannot exist otherwise).
- No row may already exist for `guideTour.id()`.

**Postconditions:**
- One row exists in `guide_tour` carrying `id`, `tour_id`, `scheduled_start`,
  `status`, `started_at` and `completed_at`. The last two are null for a tour
  created by `GuideTour.schedule(...)`.
- Domain events are **not** published here — the driver drains
  `pullDomainEvents()` and publishes via `DomainEventPublisher` (ADR-0002).

**Exceptions:** a duplicate id surfaces as Spring's `DuplicateKeyException`
translated from the H2 constraint violation. The port does not declare it; callers
treat it as an infrastructure failure, not a domain outcome.

### 2.2 findById

```
Optional<GuideTour> findById(GuideTourId guideTourId)
```

**Responsibility:** Load a guide tour by identity, reconstituted as a full aggregate.

**Preconditions:** `guideTourId` non-null.

**Postconditions:**
- `Optional.empty()` when no row matches — **not** an exception. Translating absence
  into `GuideTourNotFoundException` is the driver's job, because "not found" is a
  use case outcome (a 404) rather than a persistence failure.
- When present, the aggregate is rebuilt via `GuideTour.reconstitute(...)`, which
  deliberately skips creation-time invariant checks (`modelling.definition.md`
  § Rehydration Rule).
- The returned aggregate has **no pending domain events**.

### 2.3 update

```
void update(GuideTour guideTour)
```

**Responsibility:** Persist state changes to an existing guide tour.

**Preconditions:**
- A row must already exist for `guideTour.id()`.
- Called inside the driver's transaction.

**Postconditions:**
- **Every mutable field of the aggregate is written.** Currently `status`,
  `started_at` and `completed_at`.
- `id`, `tour_id` and `scheduled_start` are immutable in the domain, so an update
  must not change them.

> **Standing obligation, not a field list.** Adding a mutable field to `GuideTour`
> obliges you to add it to the `update` statement *and* to an
> `IT.update_changes*_inDatabase` assertion in the same increment. This is not
> hypothetical: `TourBookingJooqRepository.update` listed only `status`, so UC04's
> participant-count and capacity changes were silently discarded for months while
> its tests passed. `completed_at` was added to this contract when UC11 landed.
> The next mutable field must extend all three places again.

**Exceptions:** `IllegalStateException` if the update did not affect exactly one row.
The implementation checks the affected row count, so updating a tour that no longer
exists fails loudly rather than silently no-opping
(`GuideTourJooqRepository.update`).


## 3. Transaction Boundary

The port does **not** own transactions. The calling drivers — `StartTourDriver`
(UC05) and `CompleteTourDriver` (UC11) — are annotated `@Transactional` and own the
boundary (`architecture.definition.md` § 4.4, § 4.6). Implementations must join the
caller's transaction, never open their own.

Event publication is deferred to after commit by the `DomainEventPublisher` adapter
(ADR-0002), so a rollback cannot leak a `TourStarted` for a tour that never started,
nor a `TourCompleted` for one that never finished.


## 4. Reference Implementation

`guide.outbound.persistence.write.GuideTourJooqRepository`, with mapping in
`GuideTourMapper`.

Mapping notes that matter to the domain:

- `Instant` ↔ `TIMESTAMP` goes through `LocalDateTime` at `ZoneOffset.UTC` in both
  directions. Storing local time here would make `scheduledStart` comparisons — and
  therefore invariant I-03 (`start` not before `scheduledStart`) — depend on the
  server's zone. Pinned by
  `GuideTourJooqRepositoryIT.save_persistsScheduledStart_asUtcLocalDateTime`.
- `startedAt` and `completedAt` are both nullable, both go through the same UTC
  conversion, and both map to/from `Optional` in the mapper.
- `GuideTourId` ↔ `UUID` column; `TourId` ↔ `VARCHAR`.
- Schema: `V2__DDL_create_guide_tour.sql`, then
  `V3__DDL_add_guide_tour_completed_at.sql` (UC11). `completed_at` was added as a
  separate migration rather than by editing V2 — applied migrations are immutable.


## 5. Constraints

- MUST NOT contain business logic. Status transitions belong to the aggregate.
- MUST NOT publish domain events.
- MUST NOT expose persistence types into the core.
- MUST NOT open or commit transactions.
- MUST return `Optional.empty()` rather than throwing for a missing row.
- Implementations MAY depend on `core.domain` and `core.outport` only, never on
  `inbound.*` (`architecture.definition.md` § 6 rule 5).


## 6. Known Gaps

- **No optimistic locking.** Two concurrent `start(...)` calls on the same tour can
  both read `SCHEDULED` and both write `RUNNING`; the second wins and two
  `TourStarted` events are published. `complete(...)` has the identical race, and
  its consequence is larger: two `TourCompleted` events fan out to every ACTIVE
  booking twice (UC07). `TourBooking.markCompleted` is idempotent, so the duplicate
  is absorbed — the aggregate guard is what currently contains this, not the
  persistence layer. No version column exists in the schema. Introducing one would
  be an ADR (persistence strategy, `sdd.playbook.md` § 6 item 4).
- **No `delete`.** Deliberate — tours are cancelled (UC12), never removed.
