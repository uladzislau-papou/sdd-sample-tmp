# Port Specification – StartTour (Inport)

## Purpose

Inbound port for UC05 — the application boundary through which a guide starts a
scheduled tour.

Defines what the `guide` core accepts from any inbound adapter (REST today, a
message consumer or scheduler tomorrow) without the core knowing which
(`architecture.definition.md` § 4.2).

SDD: `documentation/use-cases/uc05-start-tour.spec.md`,
`documentation/domain/aggregate-guide-tour.spec.md`.


## 1. Interface

```
guide.core.inport.usecase.StartTourUseCase
guide.core.inport.command.StartTourCommand
guide.core.inport.result.StartTourResult
```

The standard `command` / `result` / `usecase` triple
(`architecture.definition.md` § 4.2). All three are framework-free and reference no
adapter type — no REST DTO, no jOOQ record.

```java
public interface StartTourUseCase {
    StartTourResult start(StartTourCommand command);
}

public record StartTourCommand(String guideTourId, Optional<Instant> startedAt) {}

public record StartTourResult(String status) {}
```


## 2. Method Contract

### 2.1 start

```
StartTourResult start(StartTourCommand command)
```

**Responsibility:** Load the guide tour, transition it to `RUNNING`, persist, and
publish `TourStarted` for other contexts.

**Preconditions:**
- `command.guideTourId()` must be a parseable UUID string.
- `command.startedAt()` empty means "now" — the implementation resolves it from
  `ClockPort`, never from `Instant.now()` (`architecture.definition.md` § 8).

**Postconditions:**
- The aggregate is `RUNNING` with `startedAt` set, and persisted.
- `TourStarted` is published through `DomainEventPublisher` **inside this use case's
  transaction** (`adr/0002` for the call site).

> **This postcondition used to read "queued for post-commit delivery (ADR-0002)", and that was
> the collapse, not a shorthand for it.** `domain-event-publisher.outport.spec.md` § 3 records
> that delivery timing is a property of the adapter and not of the port, so a *postcondition* —
> which is a contract — could not assert it. UC07 narrowed the port and did not propagate the
> narrowing here; `ddd-hex-reviewer` found it.
>
> **UC06 does depend on post-commit delivery, and the dependency is real.**
> `TourStartedListener` is `@TransactionalEventListener(AFTER_COMMIT)` with
> `REQUIRES_NEW`, precisely so a failure in the fan-out cannot roll back a tour that really
> started. That dependency is on the **currently wired adapter** (port spec § 3), not on this
> port — which is why it is recorded as a note here rather than as a postcondition, and why the
> outbox increment owes UC06 a migration note before it swaps the adapter.
- `StartTourResult.status()` is `"RUNNING"`.

**Exceptions** (all `RuntimeException`, declared in the interface's imports and
mapped to HTTP by `GuideExceptionHandler`):

| Exception | Condition | HTTP |
|-----------|-----------|------|
| `GuideTourNotFoundException` | no tour for the given id | 404 |
| `InvalidGuideTourStateException` | status ≠ `SCHEDULED` | 409 |
| `TourStartTooEarlyException` | `startedAt` before `scheduledStart` | 409 |

On any failure: no persistence, no event.


## 3. Usage Context

Called by `guide.inbound.rest.GuideTourController` via `GuideTourRestAPI`
(`POST /api/v1/guide-tours/{guideTourId}/start`). Controllers depend on this
interface, never on the implementing driver
(`architecture.definition.md` § 4.5).

Nothing else calls it today. UC06 in the `booking` context reacts to the resulting
`TourStarted` event rather than invoking this port — the contexts stay decoupled.


## 4. Reference Implementation

`guide.inbound.driver.StartTourDriver` — `@Service`, `@Transactional`.

Orchestration only: resolve the timestamp, load via `GuideTourRepository`, call
`guideTour.start(...)`, update, drain `pullDomainEvents()` into
`DomainEventPublisher`. It contains no status checks and no time arithmetic; both
belong to the aggregate.


## 5. Constraints

- The command carries **primitives and JDK types only** — `String` for the id, not
  `GuideTourId`. Inbound adapters must not have to construct domain value objects.
  Parsing failures surface as a 400: `UUID.fromString` raises
  `IllegalArgumentException`, which `GuideExceptionHandler` maps (§ 7).
- `Optional<Instant>` in a record component is unusual; it is used here to make
  "caller did not supply a time" explicit rather than overloading `null`.
- The result carries the status as a `String`, not `GuideTourStatus` — the enum is a
  domain type and must not leak into an API contract
  (`architecture.definition.md` § 4.5).
- MUST remain framework-free.
- MUST NOT return the aggregate.


## 6. Test Usage

- **Web slice:** `GuideTourControllerTest` mocks this port via `@MockitoBean` and
  asserts only HTTP concerns — status mapping, both body variants, the three error
  codes.
- **Orchestration:** `StartTourDriverTest` — 14 Spring-free tests with in-line stubs.
  Covers clock resolution (`start_usesClockPort_whenStartedAtIsEmpty`,
  `.start_usesProvidedStartedAt_whenPresent`), the not-found path, all three illegal
  states, the too-early guard, and that no `update` or event occurs on any failure.


## 7. Error Mapping Notes

### Malformed id is a 400

§ 5 says id parsing failures should surface as a 400. They now do.
`StartTourDriver` calls `UUID.fromString(command.guideTourId())`, which throws
`IllegalArgumentException`. Both `GuideExceptionHandler` and `BookingExceptionHandler` now
map that to 400 as a backstop, so a malformed id is a client error rather than an unmapped
500.

Found by `ddd-hex-reviewer`. The mapping also covers shared-kernel value objects, which
cannot throw a domain exception at all — see `coding-style.definition.md` § 6.2, "Shared-
kernel exemption".

Covered by `TourBookingRestControllerTest.confirm_returns400_whenTheIdentifierIsNotAUuid`
(the mapping) and
`ConfirmTourBookingDriverTest.confirm_throwsIllegalArgumentException_whenBookingIdIsMalformed`
(the source). Both, because the exception has to be raised before it can be mapped, and
asserting only the status would pass even if the 400 came from somewhere else.
