# Use Case Specification – MarkBookingActive (TourBooking)

## Status
SPECIFIED

## Purpose
Update booking status to ACTIVE when the guide starts the tour execution.

## Input Contract
Fields:
- bookingId
- startedAt (optional; default = now)
- guideTourId (optional; correlation id)

## Output Contract
Return type:
- status

Error type(s):
- NotFound
- InvalidState

## Preconditions
- Booking exists
- Booking status = CONFIRMED

## Flow
1. Load Booking aggregate (bookingId)
2. booking.markActive(startedAt)
3. Persist Booking

## Side Effects
- Persistence
- (Optional) publish BookingActivated

## Acceptance Criteria
Given a CONFIRMED booking
When MarkBookingActive is executed
Then booking status becomes ACTIVE

## Failure Scenarios
- Booking not found
- Booking is CANCELLED or COMPLETED (InvalidState)
- Booking already ACTIVE (idempotent handling recommended)

## Test Requirements
- Happy path: CONFIRMED -> ACTIVE
- Invalid state: CANCELLED -> error
- Idempotency: ACTIVE -> no-op or same result