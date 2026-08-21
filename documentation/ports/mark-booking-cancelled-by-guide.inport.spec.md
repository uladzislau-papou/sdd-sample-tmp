# Port Specification – MarkBookingCancelledByGuide (Inport)

## Purpose

The cross-context contract by which `guide` cancels the bookings attached to a tour it is
cancelling (UC09, called by UC12).

This inport is unusual in this project: every other one is reached from `inbound.rest`.
This one is reached from **another bounded context's driver**, synchronously, inside that
driver's transaction. That is the whole design, and it is why no outport exists — see § 6.

SDD: `documentation/use-cases/uc09-cancel-booking-by-guide.spec.md`,
`documentation/use-cases/uc12-cancel-tour-by-guide.spec.md`,
`documentation/adr/0008-synchronous-cross-context-cancellation.adr.md` (Rejected).


## 1. Interface

```
booking.core.inport.usecase.MarkBookingCancelledByGuideUseCase
```

```java
MarkBookingCancelledByGuideResult cancelByGuide(MarkBookingCancelledByGuideCommand command)
```

Framework-free. Command and result carry only primitives and JDK types — no domain value
object crosses the boundary (`architecture.definition.md` § 11 rule 3), so `guide` never
learns that `CancellationReason` or `CancelledBy` exist.

Implemented by `booking.inbound.driver.MarkBookingCancelledByGuideDriver`.


## 2. Input Contract

`MarkBookingCancelledByGuideCommand(String tourId, Instant cancelledAt, String guideTourId, String reason)`

| Component | Required | Notes |
|-----------|----------|-------|
| `tourId` | yes | the shared `TourId` value as a plain string; null or blank is an `IllegalArgumentException` |
| `cancelledAt` | no | the guide's cancellation time; `ClockPort` supplies it when null |
| `guideTourId` | no | correlation id, carried on the event and not stored |
| `reason` | no | free text; validated into `CancellationReason` by the driver, **before** the query runs |

**The command identifies a tour, not a booking.** An earlier revision carried a
`bookingId`, which would have forced `guide` to enumerate bookings first — a query
surface into this context and knowledge of `TourBooking`'s state model. Which bookings
are affected is this context's business, resolved by
`TourBookingRepository.findCancellableByTourId` (every non-terminal state:
`REQUESTED`, `CONFIRMED`, `ACTIVE`). One call covers the whole tour.

**`cancelledAt` is supplied by the caller here, and that is deliberate.** A REST-driven
driver must ignore any client timestamp and read the clock
(`architecture.definition.md` § 8.1) — but `guide` is not a client. It has already
recorded when it cancelled the tour, and re-dating the cancellation with `booking`'s clock
would leave the two contexts disagreeing about one moment by the duration of the call.
The fallback to `ClockPort` exists so a caller that genuinely has no timestamp still works.


## 3. Output Contract

`MarkBookingCancelledByGuideResult(int cancelledCount)` — how many bookings were
cancelled. **Zero is a valid, successful outcome**: a tour with no bookings, or one whose
bookings were all already cancelled or completed.

The return value is the point. UC06 and UC07 are events precisely because `booking` may
react to activation and completion whenever it likes; here the guide must know the bookings
were actually cancelled before reporting the tour cancelled, so the call is synchronous and
answers.

| Exception | Condition | Consequence for the caller |
|-----------|-----------|----------------------------|
| `IllegalArgumentException` | `tourId` null or blank | propagates; guide's transaction rolls back |
| `InvalidBookingRequestException` | `reason` blank or over 400 characters | propagates; rolls back |

`BookingNotFoundException` and `InvalidBookingStateException` were removed from this
contract by the per-tour rework: there is no `bookingId` to miss, and terminal-state
bookings are excluded by `findCancellableByTourId` rather than rejected. The aggregate's
guard and no-op remain as defence in depth, but nothing this driver loads can trip them.

No HTTP status mapping. There is no endpoint, so `BookingExceptionHandler` never sees
these — the caller's own adapter decides how they surface, which for UC12 means a 502
(`uc12-cancel-tour-by-guide.spec.md` § 3).


## 4. Transaction Boundary

`MarkBookingCancelledByGuideDriver` is annotated `@Transactional` with the default
`REQUIRED` propagation, so it **joins** the caller's transaction rather than opening its
own. A failure anywhere in the fan-out therefore rolls back the guide tour cancellation
too, which is the intended consistency trade-off (UC09 § 6).

This is the opposite choice from `TourCompletedListener`, which uses
`REQUIRES_NEW` + `AFTER_COMMIT`. The difference is not stylistic:

| | UC07 (completion) | UC09 (guide cancellation) |
|---|---|---|
| Trigger | domain event, post-commit | synchronous inport call |
| Propagation | `REQUIRES_NEW` | `REQUIRED` (joins caller) |
| A failure | is contained; the tour stays completed | rolls the tour cancellation back |
| Why | completion is a notification | the guide needs confirmation |

Switching either to the other model is a cross-context interaction change and an ADR
trigger (`sdd.playbook.md` § 6 item 10).


## 5. Idempotency

Idempotency has **two layers** since the per-tour rework, and the first makes the second
unreachable in practice:

1. **Query exclusion (primary).** `findCancellableByTourId` selects only non-terminal
   bookings, so already-`CANCELLED` and `COMPLETED` bookings are never loaded, never
   touched, never counted and emit no event. Calling the port twice for the same tour
   returns the count the first time and zero the second.
2. **Aggregate no-op (backstop).** Should an already-cancelled booking ever reach
   `cancel(..., GUIDE, ...)` anyway, the aggregate returns before mutating, keyed on
   `CancelledBy` — the pre-rework mechanism, deliberately retained.

This differs from UC08, where a second cancellation is a 409, and the asymmetry is
deliberate. UC12's cancellation covers every booking on a tour **in one transaction**; if
one already-handled booking threw, the entire tour cancellation would roll back and the
remaining bookings would never be touched. A participant double-cancelling has no such
blast radius and should be told they did something meaningless.

Neither layer mutates, so a guide's tour cancellation cannot overwrite a `USER`
attribution recorded earlier — pinned by
`TourBookingTest.cancel_byGuide_whenAlreadyCancelled_doesNotOverwriteAttribution`, with
the query side pinned end to end by
`CancelTourByGuideIT.cancel_skipsTerminalBookings_andStillCancelsTheTour`.


## 6. Why There Is No Outport

`guide` calls this interface directly. It does **not** own a `BookingCancellationPort`
that `booking` implements.

That design was proposed in ADR-0008 and **rejected**: the port would have had exactly one
implementation, and that implementation would have delegated to this very inport. The
indirection would have relocated the coupling behind an extra interface rather than
removing it, while making the call path harder to follow and giving `guide` a port it
cannot meaningfully substitute.

What permits the direct call is `architecture.definition.md` § 11 rule 3: a context may
depend on another context's `core.inport` — its published API — and **only from a driver**.
Enforced by `ContextRegistryTest.guideReachesBookingInport_onlyFromADriver`, which fails if
anything other than a `guide` driver imports `booking.core.inport`, and by
`.guide_doesNotImportBookingInternals`, which fails if `guide` reaches past the inport into
`booking`'s domain, outport or adapters.


## 7. Constraints

- MUST NOT be exposed over HTTP. It has no `*RestAPI` and no `rest/uc09-*.http` file.
- MUST NOT use `REQUIRES_NEW`. Joining the caller's transaction is the contract, so the
  propagation must be `REQUIRED` (the default). An earlier revision of this bullet said
  "MUST NOT open its own transaction", which is literally false: with no transactional
  caller — the state of the tree before UC12 — `REQUIRED` starts one. The constraint is
  about propagation, not about whether a transaction begins here.
- MUST remain framework-free in its signature — no Spring, no jOOQ, no domain value object.
- The caller MUST reach it only from a driver (§ 11 rule 3), never from a controller,
  listener, aggregate or outbound adapter.
- Implementations MUST leave the selection criterion to the repository query and the
  transition rules to the aggregate. A driver that checked `status() == CANCELLED` itself
  would be duplicating a domain rule in an application service
  (`architecture.definition.md` § 4.6, § 4.8); the driver only observes whether an event
  resulted and skips persist/publish if none did.


## 8. Known Gaps

Both original gaps are closed — kept as history:

- **Rollback integration test — closed with UC12**, exactly as predicted:
  `CancelTourByGuideRollbackIT.cancel_rollsBackTheTourCancellation_whenTheBookingSideFails`
  boots the whole application, fails the booking side with a mock, and asserts the guide
  tour is still `SCHEDULED` after the 502.
- **"No bulk form" — obsolete by rework** rather than by adding one. The per-tour
  signature *is* the bulk form: UC12 makes one call per tour and this context fans out
  internally over `findCancellableByTourId`. The N+1 and the partial-failure contract the
  gap worried about both dissolved with the `bookingId` parameter.

Remaining:

- ~~No dedicated jOOQ IT for `findCancellableByTourId`'s predicate.~~ **Closed.** Pinned by
  `TourBookingJooqRepositoryIT.findCancellableByTourId_returnsEveryNonTerminalBookingForThatTour`
  and `.findCancellableByTourId_returnsEmpty_whenEveryBookingIsTerminal`, mutation-verified:
  flipping `notIn` to `in` fails five tests. Reported by `spec-documenter` as a deviation from
  the pattern `findConfirmedByTourId` and `findActiveByTourId` set; it mattered more than
  those two because the predicate is negative, so a mistake over-selects — a guide cancelling
  a tour would cancel bookings that were already finished.