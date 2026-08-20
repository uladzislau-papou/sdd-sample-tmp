# Port Specification – GuideTourRepository (Outport)

## Purpose

Write-side persistence boundary for the `GuideTour` aggregate.

The `guide` core owns this abstraction; `outbound.persistence.write` provides the
implementation (`architecture.definition.md` § 4.3, § 4.6). The core never learns
that persistence is jOOQ over H2.

SDD: `documentation/domain/aggregate-guide-tour.spec.md`,
`documentation/use-cases/uc05-start-tour.spec.md`.


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
  `status` and `started_at`.
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
- `status` and `started_at` reflect the aggregate.
- `id`, `tour_id` and `scheduled_start` are immutable in the domain, so an update
  must not change them.

**Exceptions:** `IllegalStateException` if the update did not affect exactly one row.
The implementation checks the affected row count, so updating a tour that no longer
exists fails loudly rather than silently no-opping
(`GuideTourJooqRepository.update`).


## 3. Transaction Boundary

The port does **not** own transactions. `StartTourDriver` is annotated
`@Transactional` and owns the boundary (`architecture.definition.md` § 4.4,
§ 4.6). Implementations must join the caller's transaction, never open their own.

Event publication is deferred to after commit by the `DomainEventPublisher` adapter
(ADR-0002), so a rollback cannot leak a `TourStarted` for a tour that never started.


## 4. Reference Implementation

`guide.outbound.persistence.write.GuideTourJooqRepository`, with mapping in
`GuideTourMapper`.

Mapping notes that matter to the domain:

- `Instant` ↔ `TIMESTAMP` goes through `LocalDateTime` at `ZoneOffset.UTC` in both
  directions. Storing local time here would make `scheduledStart` comparisons — and
  therefore invariant I-03 (`start` not before `scheduledStart`) — depend on the
  server's zone. Pinned by
  `GuideTourJooqRepositoryIT.save_persistsScheduledStart_asUtcLocalDateTime`.
- `startedAt` is nullable and maps to/from `Optional` in the mapper.
- `GuideTourId` ↔ `UUID` column; `TourId` ↔ `VARCHAR`.
- Schema: `V2__DDL_create_guide_tour.sql`.


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
  `TourStarted` events are published. No version column exists in
  `V2__DDL_create_guide_tour.sql`. Introducing one would be an ADR (persistence
  strategy, `sdd.playbook.md` § 6 item 4).
- **No `delete`.** Deliberate — tours are cancelled (UC12), never removed.
