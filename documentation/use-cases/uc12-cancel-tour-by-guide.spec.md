# Use Case Specification – CancelTourByGuide (Guide)

## Status
IMPLEMENTED

## Bounded Context
`guide` — triggered via REST by the guide. `CancelTourByGuideDriver` orchestrates: it
cancels the guide tour, then calls `booking`'s inport
(`MarkBookingCancelledByGuideUseCase`, UC09) synchronously **inside its own transaction**.

Integration pattern: synchronous call to another context's published API, made by the
orchestrating driver. Permitted by `architecture.definition.md` § 11 rule 3, which allows a
driver to depend on another context's `core.inport` and nothing else of it. No outport is
introduced — see `adr/0008-synchronous-cross-context-cancellation.adr.md` (Rejected) for why
one was considered and dropped.

> Split out of the former `uc10-guide-actions.spec.md`. This is the **guide-side**
> action; UC09 is the booking-side reaction it drives.

## Purpose

Cancel a scheduled or running guide tour execution on the guide's initiative, and
cancel the bookings attached to it.


## 1. Intent

Let a guide call off a tour — before or during execution — and guarantee that no
booking is left believing the tour is still going ahead.


## 2. Input Contract

Fields:
- `guideTourId` — path variable (UUID format, required)
- `reason` (String) — optional, free text; non-blank and at most 400 characters

The cancellation time is **not** an input. It comes from `ClockPort`
(`architecture.definition.md` § 8.1) — a REST caller does not decide when a cancellation
happened.

Validation rules:
- `guideTourId` required, must be a valid UUID string
- `reason`, when supplied, must be non-blank and within a documented length limit


## 3. Output Contract

Return type (`CancelTourByGuideResult`):
- `status` (String – always `"CANCELLED"` on success)
- `cancelledBookings` (int – how many bookings the cancellation reached; zero is a
  valid success — a tour nobody had booked, or whose bookings were already terminal)

HTTP `200 OK`.

Error types:

| Exception | Condition | HTTP Status |
|-----------|-----------|-------------|
| `IllegalArgumentException` | `guideTourId` is not a well-formed UUID | 400 |
| `InvalidCancellationReasonException` | `reason` is blank or over 400 characters | 400 |
| `GuideTourNotFoundException` | no guide tour with the given id | 404 |
| `InvalidGuideTourStateException` | status ∉ {SCHEDULED, RUNNING} | 409 |
| `BookingCancellationFailedException` | the booking side did not complete; the whole transaction rolls back | 502 |

All errors return `{ "error": "<message>" }`.


## 4. Preconditions

- `GuideTour` exists
- Status is `SCHEDULED` or `RUNNING`

`FINISHED` is rejected — a completed tour cannot be retroactively cancelled.
`RUNNING` is allowed: aborting a tour mid-execution is the case UC09 AC-02 exists for.


## 5. Flow

1. Parse `GuideTourId` from path variable → `IllegalArgumentException` (400) if malformed
2. Build `CancellationReason` from the body when `reason` is present — **before anything is
   mutated** → `InvalidCancellationReasonException` (400) if blank or over 400 characters
3. Read `cancelledAt` from `ClockPort.now()`
4. Load aggregate via `GuideTourRepository.findById(...)` → throw `GuideTourNotFoundException` if empty
5. Call `guideTour.cancel(cancelledAt, reason)` → throws `InvalidGuideTourStateException`
   if status ∉ {SCHEDULED, RUNNING}. Before the cross-context call, so an uncancellable
   tour is rejected without touching the other context at all (AC-06)
6. Persist via `GuideTourRepository.update(guideTour)`
7. Make **one** call to `MarkBookingCancelledByGuideUseCase.cancelByGuide(...)` —
   `booking`'s inport — passing the **tourId**, the same `cancelledAt`, the guideTourId
   and the reason, **within the same transaction**, so a failure here rolls back step 6.
   Which bookings are affected is `booking`'s business, resolved by its
   `findCancellableByTourId` query — this driver never enumerates bookings and makes
   no per-booking calls. Any `RuntimeException` from the call becomes
   `BookingCancellationFailedException` (502)
8. Publish `TourCancelledByGuide` via `DomainEventPublisher` (post-commit, ADR-0002)
9. Return `{ "status": "CANCELLED", "cancelledBookings": <count from the inport result> }`


## 6. Side Effects

- Persistence: `status`, `cancelled_at`, `cancellation_reason` columns updated on `guide_tour`
- Synchronous cross-context call: bookings cancelled via `booking`'s inport (UC09)
- Event publication: `TourCancelledByGuide` published after transaction commit (ADR-0002)

Transaction boundary: steps 6 and 7 share one transaction. If the booking side
fails, the tour cancellation does not commit — the two must not diverge. This is a
deliberate consistency choice, and it is why UC09 is synchronous while UC06/UC07
are event-driven.


## 7. Acceptance Criteria

**AC-01 – Happy Path from SCHEDULED**
Given a SCHEDULED GuideTour with confirmed bookings
When CancelTourByGuide is executed
Then the tour status becomes CANCELLED, the bookings are cancelled with
`cancelledBy = GUIDE`, and `TourCancelledByGuide` is published after commit

**AC-02 – Happy Path from RUNNING**
Given a RUNNING GuideTour (tour aborted mid-execution)
When CancelTourByGuide is executed
Then the tour status becomes CANCELLED and the active bookings are cancelled

**AC-03 – Reason Recorded**
Given a cancellation with a reason supplied
When CancelTourByGuide is executed
Then the reason is persisted and passed through to the booking side

**AC-04 – Cancellation Time Comes From the Clock**
Given a cancellable GuideTour
When CancelTourByGuide is executed
Then `ClockPort` supplies `cancelledAt`, and the same value is passed to the booking side
so both contexts record one moment rather than two
And the request body carries no `cancelledAt` field, so a client-supplied timestamp
has no effect (it is ignored, not honoured — see `rest/uc12-cancel-tour-by-guide.http`)

**AC-05 – Finished Tour**
Given a FINISHED GuideTour
When CancelTourByGuide is executed
Then HTTP 409 is returned and the status is unchanged

**AC-06 – Already Cancelled**
Given a GuideTour already CANCELLED
When CancelTourByGuide is executed
Then HTTP 409 is returned and no further booking cancellations are attempted

**AC-07 – Booking Side Fails**
Given the booking side rejects the cancellation
When CancelTourByGuide is executed
Then the transaction rolls back, the tour status remains unchanged,
no `TourCancelledByGuide` is published, and HTTP 502 is returned

**AC-08 – Not Found**
Given no GuideTour exists for the given id
When CancelTourByGuide is executed
Then HTTP 404 is returned and nothing is persisted


## 8. Failure Scenarios

| Scenario | Exception | HTTP |
|----------|-----------|------|
| `guideTourId` is not a well-formed UUID | `IllegalArgumentException` | 400 |
| `reason` is blank | `InvalidCancellationReasonException` | 400 |
| `reason` exceeds 400 characters | `InvalidCancellationReasonException` | 400 |
| GuideTour does not exist | `GuideTourNotFoundException` | 404 |
| Status is FINISHED | `InvalidGuideTourStateException` | 409 |
| Status is already CANCELLED | `InvalidGuideTourStateException` | 409 |
| Booking side fails or rejects | `BookingCancellationFailedException`; whole transaction rolls back | 502 |

The ceiling is enforced by this context's own `guide.core.domain.guidetour.CancellationReason`,
not by the driver — `guide` **stores** the reason, so it owns the invariant, and an invariant
enforced in an application service is not enforced at all. It matches `booking`'s ceiling
without sharing the type, because a context's `core.domain` is closed to other contexts
(`architecture.definition.md` § 11 rule 3); only the `String` crosses the inport. Each side
pins its own constant — `guide`'s by `CancellationReasonTest.ceilingIsFourHundred` and against
the column width by `GuideTourJooqRepositoryIT.cancellationReasonColumnWidth_matchesTheDomainCeiling`.


## 9. REST Contract

Endpoint:
```
POST /api/v1/guide-tours/{guideTourId}/cancel
```

`POST`, not `DELETE`. Ruled by the maintainer, the same ruling that changed UC03/UC08:
cancelling is a state transition that leaves the tour addressable rather than a removal,
and a `DELETE` body — which is how `reason` would arrive — is dropped by some clients,
proxies and CDNs, so the reason would vanish intermittently with nothing to catch it. It
also matches `POST /guide-tours/{id}/complete`, already implemented in this context.

Request body (optional):
```json
{ "reason": "Severe weather warning" }
```

**No `cancelledAt`.** The driver reads the cancellation time from `ClockPort`
(`architecture.definition.md` § 8.1): a REST caller does not get to decide when the
cancellation happened. UC09, which receives the timestamp from this context across the
boundary, does take it as input — that is the other half of the same rule.

Response body (200 OK):
```json
{ "status": "CANCELLED", "cancelledBookings": 3 }
```

HTTP status mapping:
- `200 OK` – tour and bookings cancelled
- `400 Bad Request` – malformed `guideTourId`, or blank / over-long `reason`
- `404 Not Found` – guide tour does not exist
- `409 Conflict` – status ∉ {SCHEDULED, RUNNING}
- `502 Bad Gateway` – booking side rejected the cancellation


## 10. Definition of Done

Every test name below is the **actual** method name on disk, verified against the files.
The two gate-shaped boxes at the end close only on witnessed runs, not on prediction.

### Behaviour
- [x] AC-01 covered by `GuideTourTest.cancel_fromScheduled_transitionsToCancelled`,
      `CancelTourByGuideDriverTest.cancel_fromScheduled_returnsCancelledStatus`, and end to
      end — the only whole-application test in the suite — by
      `CancelTourByGuideIT.cancel_cancelsTheTourAndItsBookingsAcrossBothContexts`.
      (The planned name `.cancel_fromScheduled_cancelsBookingsViaPort` never existed:
      there is no port, per ADR-0008 Rejected)
- [x] AC-02 covered by `GuideTourTest.cancel_fromRunning_transitionsToCancelled`
      and `CancelTourByGuideDriverTest.cancel_fromRunning_returnsCancelledStatus`
- [x] AC-03 covered by `GuideTourTest.cancel_recordsCancelledAtAndReason`,
      `CancelTourByGuideDriverTest.cancel_passesReasonToBookingSide` and
      `GuideTourControllerTest.cancel_passesReasonToTheUseCase`
- [x] AC-04 covered by `CancelTourByGuideDriverTest.cancel_takesTheCancellationTimeFromTheClock`
      and `.cancel_passesTheSameInstantToBothSides`. The originally planned
      `.cancel_usesProvidedCancelledAt_whenNotNull` was never written and never can be:
      the command carries **no** timestamp input (§ 2, `architecture.definition.md` § 8.1)
- [x] AC-05 covered by `GuideTourTest.cancel_fromFinished_throwsInvalidGuideTourStateException`,
      `CancelTourByGuideDriverTest.cancel_throwsInvalidGuideTourStateException_whenFinished`
      and `GuideTourControllerTest.cancel_returns409_whenFinished`
- [x] AC-06 covered by `GuideTourTest.cancel_whenAlreadyCancelled_throwsInvalidGuideTourStateException`,
      `CancelTourByGuideDriverTest.cancel_whenAlreadyCancelled_doesNotCallBookingSide`
      and `.cancel_whenFinished_doesNotCallBookingSide`
- [x] AC-07 covered by `CancelTourByGuideDriverTest.cancel_propagatesBookingCancellationFailure`,
      `.cancel_whenBookingSideFails_preservesTheCause`,
      `.cancel_whenBookingSideFails_publishesNothing`,
      `GuideTourControllerTest.cancel_returns502_whenBookingSideFails`, and the real
      rollback by `CancelTourByGuideRollbackIT.cancel_rollsBackTheTourCancellation_whenTheBookingSideFails`
      (`@SpringBootTest`, deliberately not `@Transactional`)
- [x] AC-08 covered by `CancelTourByGuideDriverTest.cancel_throwsGuideTourNotFoundException_whenNotFound`
      and `GuideTourControllerTest.cancel_returns404_whenGuideTourNotFound`
- [x] `TourCancelledByGuide` emission covered by
      `GuideTourTest.cancel_emitsTourCancelledByGuideEvent`; publication by
      `CancelTourByGuideDriverTest.cancel_publishesTourCancelledByGuide`
- [x] Input validation (§ 2) covered by
      `CancelTourByGuideDriverTest.cancel_throwsIllegalArgumentException_whenGuideTourIdIsMalformed`,
      `.cancel_throwsInvalidCancellationReasonException_whenReasonIsBlank`,
      `.cancel_throwsInvalidCancellationReasonException_whenReasonExceedsTheCeiling`,
      `.cancel_validatesReasonBeforeTouchingAnything` and `.cancel_withoutReason_isPermitted`
- [x] The reason ceiling lives in the domain and is pinned twice —
      `CancellationReasonTest.ceilingIsFourHundred` for the constant and
      `GuideTourJooqRepositoryIT.cancellationReasonColumnWidth_matchesTheDomainCeiling` for the
      column width. An earlier revision cited a driver-test method of the same purpose
      (reasonCeiling_matchesTheBookingSideCeiling, deliberately un-quoted here because it no
      longer exists): the rule moved out of the driver when `ddd-hex-reviewer` found the
      driver was enforcing an invariant the aggregate should own

### Contracts
- [x] `GuideTour.cancel(Instant, CancellationReason)` exists on the aggregate, and
      `guide.core.domain.guidetour.CancellationReason` carries the length rule
- [x] `CancelTourByGuideCommand` / `Result` / `UseCase` exist in `guide.core.inport`.
      The result carries `status` **and** `cancelledBookings`
- [x] `CancelTourByGuideDriver` imports **only** `booking.core.inport` from the other
      context (exactly two types) — enforced by
      `ContextRegistryTest.guide_doesNotImportBookingInternals` and
      `.guideReachesBookingInport_onlyFromADriver`, both live evidence now that the call
      site exists. **No outport is introduced** (`adr/0008-…` Rejected)
- [x] `documentation/ports/mark-booking-cancelled-by-guide.inport.spec.md` (owned by UC09)
      records the shared transaction boundary from § 6 (`execution.playbook.md` § 3.2.3)
- [x] `shared.domain.event.TourCancelledByGuide` exists —
      `(String guideTourId, TourId tourId, Instant cancelledAt, String reason)`
- [x] `rest/uc12-cancel-tour-by-guide.http` covers 200 (with and without reason), 400
      (blank reason, malformed id), 404, 409 and 502
- [x] `V5__DDL_add_guide_tour_cancellation.sql` adds `cancelled_at` and
      `cancellation_reason` to `guide_tour`
- [x] Persistence roundtrip covered by
      `GuideTourJooqRepositoryIT.update_changesStatus_toCancelled`,
      `.cancellationFields_areEmpty_forATourThatWasNeverCancelled` and
      `.update_persistsCancellation_withoutReason`
- [x] `documentation/domain/aggregate-guide-tour.spec.md` covers the full
      SCHEDULED → RUNNING → FINISHED / CANCELLED state model, all transitions implemented

### Governance
- [x] UC09 implemented as the booking-side counterpart — reworked to a **per-tour** inport
      in this increment: the command carries `tourId`, the fan-out lives behind
      `booking`'s `findCancellableByTourId`, and `guide` never enumerates bookings
- [x] ADR question settled: `adr/0008-synchronous-cross-context-cancellation.adr.md` was
      written for the outport design and **Rejected** by the maintainer; the direct
      driver-to-inport call is sanctioned by `architecture.definition.md` § 11 rule 3.
      (An earlier revision of this box said the ADR was "Proposed" and implementation
      waited on acceptance — both stale)
- [x] This spec reconciled against the code by `spec-documenter`
- [ ] `ddd-hex-reviewer` returns `PASS` — awaiting verdict
- [ ] Quality gates green (`test.definition.md` § 7) — awaiting a witnessed run
