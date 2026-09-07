# Domain Specification – TourBooking

## Purpose

A customer-side reservation for a guided tour: requested, confirmed, and made active when
the tour it belongs to starts.

> **Reduced for the template.** The reference example covers `request`, `confirm` and
> `markActive`. `cancel` (UC03/UC08/UC09), `markCompleted` (UC07) and `changeParticipants`
> (UC04) were part of the original and are not part of the example — see
> `project.definition.md` non-goals. The three that remain were chosen because together
> they form a connected chain: creation, a transition reached over HTTP, and a transition
> reached only by a domain event from another context.


## 1. Aggregate Root

Name: `TourBooking`
Package: `booking.core.domain.tourbooking`

| Property | Type | Mutable | Meaning |
|----------|------|---------|---------|
| `bookingId` | `BookingId` | no | identity |
| `tourId` | `TourId` | no | the catalogue tour, from `shared.domain` |
| `tourDate` | `TourDate` | no | the scheduled calendar date |
| `contact` | `ParticipantContact` | no | who to contact |
| `participantCount` | `ParticipantCount` | yes, `private set` | how many people |
| `availableCapacity` | `AvailableCapacity` | yes, `private set` | capacity observed when the decision was made |
| `status` | `TourBookingStatus` | yes, `private set` | lifecycle position |

Not a `data class`, for the reason given in `coding-style.definition.md` § 2.1.


## 2. Invariants (Always-Valid)

- **I-01** Identity, tour, date and contact are present and never change.
- **I-02** `participantCount >= 1`. Violation raises `InvalidBookingRequestException`.
- **I-03** At creation, `participantCount <= availableCapacity`. Violation raises
  `CapacityExceededException` — a *conflict*: the request is well-formed, the world does not
  allow it right now.
- **I-04** At creation, `tourDate` is strictly after the calendar day of `now` (UTC).
  Violation raises `InvalidBookingRequestException`.
- **I-05** `confirm` is permitted only from `REQUESTED`.
- **I-06** `markActive` is permitted only from `CONFIRMED`, and is an idempotent no-op when
  already `ACTIVE`. `REQUESTED → ACTIVE` is rejected: tolerating it would let a booking
  nobody confirmed be carried along by a tour starting.
- **I-07** A rejected transition changes nothing — no status, no fields, no event.

## 2a. Value Objects

| Type | Rule | On violation |
|------|------|--------------|
| `BookingId` | wraps a `UUID`; `generate()` mints a new one | — |
| `TourDate` | wraps a `LocalDate`; `isInFuture(now)` compares calendar days at UTC | — |
| `ParticipantCount` | `>= 1` | `InvalidBookingRequestException` |
| `ParticipantContact` | name and email both non-blank | `InvalidBookingRequestException` |
| `AvailableCapacity` | `>= 0` | `IllegalArgumentException` — see below |
| `TourId` | non-blank; lives in `shared.domain` | `IllegalArgumentException` — see below |

**Why two of them differ.** The rule is the *source* of the value, not what validates it
upstream (`coding-style.definition.md` § 6.2). `ParticipantCount` and `ParticipantContact`
are built from a command, so the domain is the last guard guaranteed to run and they must
raise a domain exception. `AvailableCapacity` never comes from a command — only from the
`AvailabilityChecker` outport or from reconstitution — so a negative value is a programmer
error. `TourId` is exempt for a different reason: it lives in `shared.domain`, which may not
depend on any bounded context, so no domain exception is available to it at all
(§ 6.3). Both are mapped to 400 by every `*ExceptionHandler` as a backstop.


## 3. State Model

Possible states: `REQUESTED`, `CONFIRMED`, `ACTIVE`, `CANCELLED`, `COMPLETED`

Allowed transitions in this example:
- `REQUESTED → CONFIRMED` via `confirm` (UC02)
- `CONFIRMED → ACTIVE` via `markActive` (UC06)
- `ACTIVE → ACTIVE` — idempotent no-op

`CANCELLED` and `COMPLETED` remain declared but **unreachable** here: they are terminal
states of the transitions the example does not carry. They are kept, unlike
`GuideTourStatus`'s removed values, because `markActive` must still *reject* them — I-06 is
about what may not happen, and a rejection needs something to reject.


## 4. Behavior

### `request(...)` — static factory

Validates I-04 then I-03, creates the aggregate in `REQUESTED`, records
`TourBookingRequested`. Takes `now` as a parameter; the domain never reads a clock
(`architecture.definition.md` § 8).

### `reconstitute(...)` — static factory

Rebuilds a persisted booking with no pending events and **no** invariant re-checks.
Callable only from `..outbound.persistence..`, enforced by
`ClassRoleRulesTest.reconstituteIsCalledOnlyByPersistence`.

### `confirm(Instant now)`

Guards I-05, sets `CONFIRMED`, records `TourBookingConfirmed`.

### `markActive(Instant startedAt, String? guideTourId)`

Returns immediately when already `ACTIVE` — before mutating anything, so a redelivered
event cannot overwrite state or emit a second event. Then guards I-06, sets `ACTIVE`,
records `BookingActivated`.

Idempotency here is a requirement, not politeness: the caller is an `AFTER_COMMIT` listener
and may see the same event twice.

`guideTourId` is a plain nullable `String` on purpose — the identity belongs to the `guide`
context, so `booking` treats it as opaque and never parses or branches on it (ADR-0005).

### `pullDomainEvents()`

Returns and clears the recorded events; a second call returns empty.


## 5. Domain Events

| Event | Recorded by | Fields |
|-------|-------------|--------|
| `TourBookingRequested` | `request` | `bookingId`, `tourId`, `tourDate`, `participantCount`, `occurredAt` |
| `TourBookingConfirmed` | `confirm` | `bookingId`, `occurredAt` |
| `BookingActivated` | `markActive` | `bookingId`, `activatedAt`, `guideTourId?` |

All three live in `booking.core.domain.tourbooking.event` — they are internal facts of this
context. Contrast `TourStarted`, which lives in `shared.domain.event` because another
context consumes it.


## 6. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| `tourDate` not in the future | `InvalidBookingRequestException` | 400 |
| `participantCount < 1` | `InvalidBookingRequestException` | 400 |
| blank contact name or email | `InvalidBookingRequestException` | 400 |
| count exceeds available capacity | `CapacityExceededException` | 409 |
| `confirm` from a state other than `REQUESTED` | `InvalidBookingStateException` | 409 |
| `markActive` from `REQUESTED`, `CANCELLED` or `COMPLETED` | `InvalidBookingStateException` | 409 |
| no booking with that identity | `BookingNotFoundException` (raised by the driver) | 404 |
| availability system unreachable | `AvailabilityUnavailableException` | 502 |
| identifier is not a UUID | `IllegalArgumentException` | 400 — backstop |


## 7. Test Requirements

| Invariant | Test |
|-----------|------|
| I-02 | `ParticipantCountTest.construction_throwsInvalidBookingRequestException_whenCountIsZero`, `ParticipantCountTest.construction_throwsInvalidBookingRequestException_whenCountIsNegative` |
| I-03 | `TourBookingTest.request_throwsCapacityExceededException_whenCountExceedsCapacity` and `TourBookingTest.request_succeeds_whenCountEqualsCapacity` — the boundary is inclusive, so both sides are asserted |
| I-04 | `TourBookingTest.request_throwsInvalidBookingRequestException_whenTourDateIsNotInFuture`, `TourDateTest.isInFuture_isFalse_forTheSameDay`, `TourDateTest.isInFuture_isFalse_forAPastDate` |
| I-05 | `TourBookingTest.confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed` |
| I-06 | `TourBookingTest.markActive_throwsInvalidBookingStateException_whenStillRequested`, `TourBookingTest.markActive_throwsInvalidBookingStateException_whenCancelled`, `TourBookingTest.markActive_throwsInvalidBookingStateException_whenCompleted`, `TourBookingTest.markActive_isIdempotentNoOp_whenAlreadyActive` |
| I-07 | `TourBookingTest.confirm_leavesStatusUnchanged_whenStateInvalid` |

Transition and event coverage:

- `TourBookingTest.request_setsStatus_toRequested`, `TourBookingTest.request_storesAllFields_asGiven`
- `TourBookingTest.request_recordsExactlyOneEvent_tourBookingRequested`
- `TourBookingTest.pullDomainEvents_returnsEmpty_onSecondCall`
- `TourBookingTest.confirm_transitionsStatus_toConfirmed`, `TourBookingTest.confirm_recordsTourBookingConfirmedEvent`
- `TourBookingTest.markActive_transitionsStatus_toActive`, `TourBookingTest.markActive_recordsBookingActivatedEvent_carryingTheGuideTourId`, `TourBookingTest.markActive_acceptsNullGuideTourId`
- `TourBookingTest.reconstitute_recordsNoEvents`
- `BookingIdTest.equality_isStructural`, `ParticipantContactTest.construction_throwsInvalidBookingRequestException_whenNameIsBlank`

Persistence roundtrip: `TourBookingJpaRepositoryIT.save_thenFindById_roundTripsEveryField`,
`TourBookingJpaRepositoryIT.update_persistsTheStatusTransition`,
`TourBookingJpaRepositoryIT.findById_returnsNull_whenNoBookingHasThatIdentity` and
`TourBookingJpaRepositoryIT.update_writesEveryMutableField_notOnlyTheOneTheUseCaseChanged`.

Every citation above is checked by `SpecCitationsTest`.
