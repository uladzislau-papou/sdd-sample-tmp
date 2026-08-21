# Use Case Specification – MarkBookingCancelledByGuide (TourBooking)

## Status
IMPLEMENTED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger/Caller: `guide` — invokes `booking` when a guide cancels a tour (UC12).
Integration pattern: **synchronous call to this context's inport**, made by
`guide.inbound.driver.CancelTourByGuideDriver` inside its own transaction.
`architecture.definition.md` § 11 rule 3 permits a driver to depend on another context's
`core.inport` — its published API — and this use case *is* that inport.

No outport is involved. An earlier revision proposed a `BookingCancellationPort` owned by
`guide`; it was rejected because its one implementation would have delegated to this very
inport, relocating the coupling rather than removing it. See
`adr/0008-synchronous-cross-context-cancellation.adr.md`.

> **Integration pattern differs from UC06/UC07 deliberately.** Activation and
> completion are *notifications* — `booking` may react whenever it likes, so they are
> event-driven and post-commit. Guide-initiated cancellation is different: the guide
> needs to know the bookings were actually cancelled before reporting the tour
> cancelled, so it is a synchronous call with a return value. Changing this to
> event-driven (or the reverse) is a cross-context interaction model change and an
> ADR trigger (`sdd.playbook.md` § 6, item 10).
>
> **Depends on UC08.** The `CancelledBy` concept and the extended `cancel(...)`
> signature are introduced there. UC09 adds the `GUIDE` attribution and the
> cross-context port.

## Purpose

Cancel every cancellable booking for a tour the guide cancelled, recording guide
attribution.


## 1. Intent

Let the `guide` context cancel the bookings attached to a tour it is cancelling,
without either context reaching into the other's aggregate — or even learning which
bookings exist.


## 2. Input Contract

Fields (via `MarkBookingCancelledByGuideCommand`, not via REST):
- `tourId` — required; the tour whose bookings are to be cancelled
- `cancelledAt` (Instant) — optional; defaults to `ClockPort.now()`
- `guideTourId` — optional correlation id
- `reason` (String) — optional, free text

**The command identifies a tour, not a booking.** An earlier revision carried a
`bookingId`, which would have forced `guide` to enumerate bookings first — meaning a
query surface into this context and knowledge of `TourBooking`'s state model. The set of
affected bookings is this context's business, resolved by
`TourBookingRepository.findCancellableByTourId` (every non-terminal state). `tourId` is
the shared `TourId` value as a plain `String` — the identity is owned by no single
context (ADR-0005 category 3), and commands carry primitives regardless.

Validation rules:
- `tourId` required; null or blank is an `IllegalArgumentException` (from the `TourId`
  constructor)
- `reason`, when supplied, is validated into `CancellationReason` by the driver
  (non-blank, ≤ 400 characters), **before** the query runs — a bad reason fails without
  touching the database and regardless of how many bookings the tour has
- The port is called inside the guide's transaction; see § 6


## 3. Output Contract

Return type:
- `cancelledCount` (int) via `MarkBookingCancelledByGuideResult` — how many bookings
  were actually cancelled. **Zero is a valid, successful outcome**: a tour with no
  bookings, or one whose bookings were all already cancelled or completed. The guide
  still cancelled the tour.

Error types:

| Exception | Condition | Surfaced as |
|-----------|-----------|-------------|
| `IllegalArgumentException` | `tourId` null or blank | propagated to caller |
| `InvalidBookingRequestException` | `reason` blank or over 400 characters | propagated to caller |

`BookingNotFoundException` and `InvalidBookingStateException` are **no longer reachable**
through this use case. The per-tour rework removed both failure modes: there is no
`bookingId` to miss, and terminal-state bookings (`COMPLETED`, `CANCELLED`) are excluded
by the query rather than rejected by the aggregate. The aggregate's own guard and no-op
remain in force as defence in depth (§ 4), but nothing this driver loads can trip them.

No HTTP status mapping — reached through this context's **inport**, not an endpoint. (An
earlier revision said "outport"; that was left over from the design ADR-0008 rejected.)


## 4. Preconditions

- None on the tour: a tour with no cancellable bookings is a success returning zero
- A booking is cancelled if its status is `REQUESTED`, `CONFIRMED` or `ACTIVE` — every
  non-terminal state, selected by `findCancellableByTourId`

`ACTIVE` **is** allowed: a tour aborted mid-execution must be cancellable. This is
the one transition where a guide may override a running tour, and it is the reason
UC08 forbids the participant from doing the same.

`REQUESTED` is allowed too. An earlier revision listed only `CONFIRMED` and `ACTIVE`,
which would have left any not-yet-confirmed booking for a cancelled tour stranded in
`REQUESTED` forever, waiting on a tour that will never run. A guide cancelling a tour
cancels everything attached to it that has not already reached a terminal state.

`COMPLETED` is not cancelled — a finished booking cannot be retroactively cancelled. It
is **excluded by the query**, not rejected: the fan-out never asks the aggregate to do
something it would refuse, so one completed booking cannot block the rest of the batch.
The aggregate's guard (`InvalidBookingStateException` for a direct
`cancel(..., GUIDE, ...)` on a `COMPLETED` booking) holds independently, as defence in
depth.

`CANCELLED` is likewise excluded by the query, and — should one ever reach the aggregate
anyway — an idempotent no-op, **not** a rejection. This differs from UC08, where a second
cancellation is a 409, and the difference is deliberate: a participant double-cancelling
is a client error worth reporting, whereas UC12's cancellation reaching a booking somebody
already cancelled is expected. Throwing there would roll back the entire tour cancellation
— the call is inside the guide's transaction (§ 6) — so one already-handled booking would
block the other twenty. The aggregate therefore branches on `cancelledBy`, which it
already does to choose the event type. In both cases the original attribution survives,
because exclusion and no-op alike touch nothing.

**Consequence, accepted deliberately.** The exclusion keys on status alone, not on who
cancelled first. So when a guide's tour cancellation covers a booking a *participant* had
already cancelled, the call succeeds and emits **no** `BookingCancelledByGuide`. Any future
consumer of that event — a refund policy, a notification service — therefore never learns
that the tour cancellation touched that booking. That is the right trade here: the booking
is already cancelled, the participant already got their `BookingCancelledByUser`, and
emitting a second cancellation event for one booking would tell consumers it was cancelled
twice. But it is a real gap for anything that wants "every booking affected by tour X being
called off", and such a consumer should listen to `TourCancelledByGuide` on the guide side
instead, which fires once per tour regardless. Raised by `ddd-hex-reviewer`, which noted the
behaviour was asserted as intended but justified only as "the attribution survives".


## 5. Flow

1. `guide`'s `CancelTourByGuideDriver` makes **one** call to
   `MarkBookingCancelledByGuideUseCase.cancelByGuide(command)` per tour
2. `MarkBookingCancelledByGuideDriver` builds `TourId` → throws
   `IllegalArgumentException` if null or blank
3. Build `CancellationReason` when `reason` is present → throws
   `InvalidBookingRequestException` if blank or over 400 characters. Before the query,
   so a bad reason costs no database round trip regardless of how many bookings exist
4. Resolve `cancelledAt`: the command's value when present, else `ClockPort.now()`
5. Load the affected aggregates via
   `TourBookingRepository.findCancellableByTourId(tourId)` — the selection criterion
   (every non-terminal state) lives in the query, not in this driver and not in the
   caller. An empty list is not an error
6. For each booking, call `booking.cancel(cancelledAt, CancelledBy.GUIDE, reason,
   guideTourId)` — the 4-arg overload
7. Pull the domain events per booking. **If none resulted** (the aggregate's idempotent
   no-op — unreachable via the query, kept as defence in depth), skip persist and
   publish for that booking — same shape as `MarkBookingCompletedDriver`
8. Otherwise persist via `TourBookingRepository.update(booking)`, publish
   `BookingCancelledByGuide` via `DomainEventPublisher`, and count it
9. Return `cancelledCount` to the caller — zero when nothing was cancellable


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancelled_by` and `cancellation_reason` columns
  updated — the four UC08 added. **No `guide_tour_id` column**, and no new migration.

`guideTourId` is carried on `BookingCancelledByGuide` and not stored. An earlier revision
of this section listed a `guide_tour_id` column and AC-05 said the correlation id was
"persisted". Dropped deliberately, on the rule this project has been applying: the
aggregate stores what an invariant or an acceptance criterion must be able to observe, and
nothing else. UC08 stores `cancelledAt` because AC-06 has to prove an existing attribution
survived; nothing here needs to query which guide tour caused a cancellation. UC06 and UC07
already treat the same `guideTourId` as event payload only, so a column here would make
cancellation the lone exception.
- Event publication: `BookingCancelledByGuide` published after transaction commit (ADR-0002)

Transaction boundary: the port is invoked **within** the guide's transaction, so a
failure to cancel bookings rolls back the tour cancellation. This is the intended
consistency trade-off and is what distinguishes UC09 from UC06/UC07.


## 7. Acceptance Criteria

**AC-01 – Happy Path, CONFIRMED booking**
Given a tour with a booking in CONFIRMED state
When MarkBookingCancelledByGuide is executed for that tour
Then the booking's status becomes CANCELLED with `cancelledBy = GUIDE`
And `BookingCancelledByGuide` is published after commit
And the booking is counted in `cancelledCount`

**AC-02 – Happy Path, ACTIVE booking**
Given a tour with a booking in ACTIVE state (tour aborted mid-execution)
When MarkBookingCancelledByGuide is executed for that tour
Then the booking's status becomes CANCELLED with `cancelledBy = GUIDE`

**AC-03 – Completed Booking Skipped**
Given a tour whose bookings include one in COMPLETED state
When MarkBookingCancelledByGuide is executed for that tour
Then the completed booking is excluded by the query, untouched and uncounted,
no exception is thrown, and the remaining bookings are still cancelled

**AC-04 – Already Cancelled Booking Skipped**
Given a tour whose bookings include one already CANCELLED
When MarkBookingCancelledByGuide is executed for that tour
Then that booking is excluded, no event is published for it, nothing about it is
persisted, and the existing attribution — including a `USER` one — is not overwritten
(the aggregate's idempotent no-op backs this up should such a booking ever be loaded)

**AC-05 – Correlation Recorded**
Given a cancellation carrying a `guideTourId`
When MarkBookingCancelledByGuide is executed
Then the correlation id is carried on each emitted `BookingCancelledByGuide`
(not persisted — see § 6)

**AC-07 – Happy Path, REQUESTED booking**
Given a tour with a booking in REQUESTED state, never confirmed
When MarkBookingCancelledByGuide is executed for that tour
Then the booking's status becomes CANCELLED with `cancelledBy = GUIDE`
Because otherwise the booking waits forever on a tour that will never run

**AC-06 – Nothing to Cancel**
Given a tour with no bookings, or only terminal-state ones
When MarkBookingCancelledByGuide is executed for that tour
Then the call succeeds with `cancelledCount = 0`, nothing is persisted, and no
event is published — the guide still cancelled the tour
(Replaces the pre-rework "Booking Not Found" criterion: with no `bookingId` input,
that failure mode no longer exists)


## 8. Failure Scenarios

| Scenario | Exception | Handling |
|----------|-----------|----------|
| `tourId` null or blank | `IllegalArgumentException` | propagated; guide's transaction rolls back |
| `reason` blank or over 400 characters | `InvalidBookingRequestException` | propagated; guide's transaction rolls back |
| Tour has no bookings, or only terminal ones | none | success; returns `cancelledCount = 0` |
| Booking in COMPLETED state | none | excluded by `findCancellableByTourId`; aggregate guard remains as backstop |
| Booking already CANCELLED | none | excluded by the query; aggregate no-op remains as backstop |

`BookingNotFoundException` and `InvalidBookingStateException` no longer appear here —
both were removed from the reachable surface by the per-tour rework (§ 3).


## 9. REST Contract

`Not applicable — reached through this context's inport, not over HTTP.`

`guide`'s driver calls `MarkBookingCancelledByGuideUseCase` directly. No endpoint, so no
`rest/uc09-*.http` file is required.


## 10. Definition of Done

Behaviour and contracts are implemented, including the **per-tour rework** that landed
with UC12 (the command now carries `tourId`, the result a count, and the fan-out lives
behind `findCancellableByTourId`). Every test name below is the **actual** method name on
disk, verified against the files. The two gate-shaped boxes close only on witnessed runs.

### Behaviour
- [x] AC-01 covered by `TourBookingTest.cancel_byGuide_fromConfirmed_recordsGuideAttribution`
      and `MarkBookingCancelledByGuideDriverTest.cancel_fromConfirmed_returnsCancelledStatus`
      / `.cancel_fromConfirmed_recordsGuideAttribution` / `.cancel_fromConfirmed_callsUpdateOnRepository`
- [x] AC-02 covered by `TourBookingTest.cancel_byGuide_fromActive_transitionsToCancelled`
      and `MarkBookingCancelledByGuideDriverTest.cancel_fromActive_returnsCancelledStatus`.
      The asymmetry is pinned from both sides:
      `TourBookingTest.cancel_byUser_fromActive_stillThrowsInvalidBookingStateException`
      fails if widening the guard for the guide also widened it for the participant
- [x] AC-07 covered by `TourBookingTest.cancel_byGuide_fromRequested_transitionsToCancelled`
      and `MarkBookingCancelledByGuideDriverTest.cancel_fromRequested_returnsCancelledStatus`.
      Added when § 4 was corrected; the original spec would have stranded unconfirmed
      bookings. `ddd-hex-reviewer` noted the behaviour had arrived without an acceptance
      criterion, which is why AC-07 now exists rather than just the tests
- [x] AC-03 covered by `MarkBookingCancelledByGuideDriverTest.cancel_skipsCompletedBookings_withoutThrowing`
      (query exclusion — replaced the pre-rework `.cancel_throwsInvalidBookingStateException_whenCompleted`)
      and, end to end against the real SQL predicate,
      `CancelTourByGuideIT.cancel_skipsTerminalBookings_andStillCancelsTheTour`. The
      aggregate's backstop guard stays pinned by
      `TourBookingTest.cancel_byGuide_fromCompleted_throwsInvalidBookingStateException`
- [x] AC-04 covered by `TourBookingTest.cancel_byGuide_whenAlreadyCancelled_isIdempotentNoOp`,
      `.cancel_byGuide_whenAlreadyCancelled_doesNotOverwriteAttribution`, and
      `MarkBookingCancelledByGuideDriverTest.cancel_idempotent_doesNotCallUpdate`
      / `.cancel_idempotent_doesNotPublishEvent`
      / `.cancel_idempotent_returnsZero_whenBookingAlreadyCancelled`
      (renamed from `.cancel_idempotent_returnsCancelledStatus` when the result became a count)
- [x] AC-05 covered by `TourBookingTest.cancel_byGuide_carriesGuideTourIdOntoEvent_andDoesNotStoreIt`
      and `MarkBookingCancelledByGuideDriverTest.cancel_publishesEventCarryingGuideTourIdAndReason`
- [x] AC-06 covered by `MarkBookingCancelledByGuideDriverTest.cancel_returnsZero_whenTourHasNoBookings`
      and `.cancel_returnsZero_whenEveryBookingIsAlreadyTerminal`
      (the pre-rework `.cancel_throwsBookingNotFoundException_whenNotFound` was removed with
      the failure mode it tested)
- [x] Input validation covered by
      `MarkBookingCancelledByGuideDriverTest.cancel_throwsIllegalArgumentException_whenTourIdIsBlank`,
      `.cancel_throwsInvalidBookingRequestException_whenReasonIsBlank` and
      `.cancel_withoutReason_stillCancels`
- [x] `BookingCancelledByGuide` emission covered by
      `TourBookingTest.cancel_byGuide_publishesBookingCancelledByGuideEvent`
- [x] The caller's timestamp is honoured rather than overwritten —
      `MarkBookingCancelledByGuideDriverTest.cancel_usesTheCallersCancelledAt_whenSupplied`,
      with `.cancel_usesClockPort_whenCancelledAtIsNull` for the fallback
      (`architecture.definition.md` § 8.1)
- [x] Transaction rollback behaviour (§ 6) covered by
      `CancelTourByGuideRollbackIT.cancel_rollsBackTheTourCancellation_whenTheBookingSideFails`
      and `.cancel_preservesTheUnderlyingCauseOnTheFailure` — landed with UC12, exactly as
      this box predicted. The former Known Gap in the inport spec § 8 is closed

### Contracts
- [x] `MarkBookingCancelledByGuideCommand` / `Result` / `UseCase` exist in the
      `booking.core.inport` triple — this is the whole cross-context contract; **no outport
      is introduced** (`adr/0008-…` Rejected). The command carries `tourId`, the result
      `cancelledCount`
- [x] `MarkBookingCancelledByGuideDriver` exists in `booking.inbound.driver`, annotated
      `@Transactional` with default `REQUIRED` propagation so it joins the caller's transaction
- [x] `TourBookingRepository.findCancellableByTourId(TourId)` exists on the outport,
      documented in `ports/tour-booking-repository.outport.spec.md` § 2.7. Its SQL predicate
      is exercised end to end by `CancelTourByGuideIT.cancel_skipsTerminalBookings_andStillCancelsTheTour`
      and `.cancel_leavesBookingsOfOtherToursAlone` (no dedicated
      `TourBookingJooqRepositoryIT` method — noted in the port spec)
- [x] `documentation/ports/mark-booking-cancelled-by-guide.inport.spec.md` written, covering
      the transaction boundary (§ 4), idempotency (§ 5), why there is no outport (§ 6) and
      `guide` as the caller
- [x] `ContextRegistryTest.guide_doesNotImportBookingInternals` is **live evidence today**:
      it constrains all of `guide` and fails the moment any `guide` class imports
      `booking.core.domain`, `.core.outport` or an adapter
- [x] `guide` imports `booking.core.inport` **only from a driver** —
      `ContextRegistryTest.guideReachesBookingInport_onlyFromADriver` is now exercised by a
      real call site: `CancelTourByGuideDriver` imports exactly two `booking.core.inport`
      types and nothing else from `booking`. (Before UC12 the rule was satisfied trivially)
- [x] `BookingCancelledByGuide` exists in `booking.core.domain.tourbooking.event`, carrying
      `bookingId`, `cancelledAt`, `reason` and `guideTourId`
- [x] Persistence roundtrip covered by
      `TourBookingJooqRepositoryIT.update_persistsGuideCancellationAttribution`, plus
      `.update_persistsCancelledBy_asTheEnumName` for the enum mapping
- [x] `documentation/domain/aggregate-tour-booking.spec.md` § 3 state model updated with the
      ACTIVE → CANCELLED transition and the caller-dependent guard

### Governance
- [x] UC08 implemented first — `CancelledBy` and the extended `cancel(...)` come from there
- [x] UC12 (CancelTourByGuide, guide side) exists as the caller —
      `guide.inbound.driver.CancelTourByGuideDriver`, one call per tour
- [x] **No ADR required.** ADR-0008 was written for the outport design and **Rejected** by
      the maintainer; the direct driver-to-inport call it was replaced with is already
      sanctioned by `architecture.definition.md` § 11 rule 3, so no trigger fires. An
      earlier revision of this box said the ADR was "Proposed" and that implementation
      waited on its acceptance — both stale
- [x] This spec reconciled against the code by `spec-documenter` — twice: once after the
      original per-booking implementation, again after the per-tour rework (§ 2, § 3, § 5,
      § 7, § 8 all reframed in the second pass)
- [ ] `ddd-hex-reviewer` returns `PASS` — awaiting the post-rework verdict
- [ ] Quality gates green (`test.definition.md` § 7) — awaiting a witnessed run. An earlier
      tick (323 tests) predates the per-tour rework and no longer counts as evidence
