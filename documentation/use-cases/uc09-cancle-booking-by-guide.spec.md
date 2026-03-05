# Use Case Specification – MarkBookingCancelledByGuide (TourBooking)

## Status
SPECIFIED

## Bounded Context
Owner: `booking` — holds and transitions the `TourBooking` aggregate.
Caller: `guide` — invokes `booking` synchronously when a guide cancels a tour.
Integration pattern: synchronous outport call; `guide` defines a `BookingCancellationPort` outport, `booking` provides the implementation.

## Purpose
Cancel a booking due to a guide-initiated tour cancellation.

## Input Contract
Fields:
- bookingId
- cancelledAt (optional; default = now)
- guideTourId (optional; correlation id)
- reason (optional)

## Preconditions
- Booking exists
- Booking status in {CONFIRMED, ACTIVE}
  (Decide if ACTIVE is allowed; often yes if tour is aborted.)

## Flow
1. Load Booking aggregate (bookingId)
2. booking.cancel(cancelledAt, cancelledBy=GUIDE, reason)
3. Persist Booking

## Side Effects
- Persistence
- (Optional) publish BookingCancelledByGuide

## Acceptance Criteria
Given a CONFIRMED or ACTIVE booking
When MarkBookingCancelledByGuide is executed
Then booking status becomes CANCELLED

## Failure Scenarios
- NotFound
- InvalidState (COMPLETED)
- Already CANCELLED -> idempotent no-op recommended