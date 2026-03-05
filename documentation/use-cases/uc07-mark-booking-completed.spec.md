# Use Case Specification – MarkBookingCompleted (TourBooking)

## Status
SPECIFIED

## Purpose
Update booking status to COMPLETED when the guide completes the tour execution.

## Input Contract
Fields:
- bookingId
- completedAt (optional; default = now)
- guideTourId (optional; correlation id)

## Output Contract
Return type:
- status

Error type(s):
- NotFound
- InvalidState

## Preconditions
- Booking exists
- Booking status = ACTIVE
  (Optionally allow CONFIRMED -> COMPLETED if you want to tolerate missing TourStarted events; usually not recommended.)

## Flow
1. Load Booking aggregate (bookingId)
2. booking.markCompleted(completedAt)
3. Persist Booking

## Side Effects
- Persistence
- (Optional) publish BookingCompleted

## Acceptance Criteria
Given an ACTIVE booking
When MarkBookingCompleted is executed
Then booking status becomes COMPLETED

## Failure Scenarios
- Booking not found
- Invalid state: REQUESTED/CONFIRMED/CANCELLED -> error
- Already COMPLETED -> idempotent no-op recommended

## Test Requirements
- Happy path: ACTIVE -> COMPLETED
- Invalid state: CONFIRMED -> error
- Idempotency: COMPLETED -> no-op