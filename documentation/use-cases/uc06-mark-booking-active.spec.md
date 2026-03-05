# Use Case Specification – MarkBookingActive (TourBooking)

## Status
IMPLEMENTED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Trigger: `guide` — publishes `TourStarted` (in `shared.domain.event`) after a guide starts a tour (UC05).
Integration pattern: event-driven; `booking` listens via `TourStartedListener` (`@TransactionalEventListener`).

## Context
This use case is **not exposed via REST**. It is triggered exclusively by the `guide` bounded
context: when a guide starts a tour (UC05), the `TourStarted` integration event is published.
A `TourStartedListener` in the `booking` context reacts to this event and marks all CONFIRMED
bookings for that tour as ACTIVE.

Only guides can initiate this transition — the architecture enforces this through the
event-driven boundary: there is no direct HTTP endpoint for booking activation.

## Purpose
Transition all CONFIRMED bookings for a given tour to ACTIVE when the guide starts tour
execution. If a booking is already ACTIVE the call is a no-op (idempotent).

## Trigger
`TourStarted` integration event published to `shared.domain.event.TourStarted` after
the guide tour's transaction commits (see UC05 and ADR 0002).

## Input (from event)
- `tourId` — identifies which tour's bookings to activate
- `startedAt` — the actual tour start time (from the guide tour)
- `guideTourId` — correlation ID linking booking activation to the guide tour execution

## Flow
1. `TourStartedListener` receives `TourStarted` event (AFTER_COMMIT, new transaction)
2. Load all `TourBooking` aggregates for `event.tourId()`
3. For each booking in CONFIRMED status:
   a. `booking.markActive(startedAt, guideTourId)`
   b. Persist booking
   c. Publish `BookingActivated`

## Side Effects
- Persistence (one update per CONFIRMED booking)
- Publishes `BookingActivated` domain event per activated booking

## Acceptance Criteria
Given a CONFIRMED booking for tour T
When guide starts tour T (TourStarted event fires)
Then booking status becomes ACTIVE and BookingActivated is published

## Failure Scenarios
- Booking not found → no-op (booking may have been cancelled between confirmation and tour start)
- Booking already ACTIVE → idempotent no-op (no update, no event)
- Booking in CANCELLED/COMPLETED → filtered out before processing; not activated

## Test Requirements
- Happy path: CONFIRMED → ACTIVE via MarkBookingActiveDriver
- Idempotency: ACTIVE → no-op (no update, no event)
- Invalid state: CANCELLED → exception (domain invariant)
