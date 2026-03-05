# Use Case Specification – CancelBookingByUser (TourBooking)

## Status
SPECIFIED

## Purpose
Cancel a booking initiated by the user.

## Input Contract
Fields:
- bookingId
- cancelledAt (optional; default = now)
- reason (optional)

## Preconditions
- Booking exists
- Booking status in {REQUESTED, CONFIRMED}
- Booking MUST NOT be ACTIVE or COMPLETED (policy decision; recommended)

## Flow
1. Load Booking aggregate (bookingId)
2. booking.cancel(cancelledAt, cancelledBy=USER, reason)
3. Persist Booking
4. Publish BookingCancelledByUser (domain event)

## Acceptance Criteria
Given a REQUESTED or CONFIRMED booking
When CancelBookingByUser is executed
Then booking status becomes CANCELLED
And BookingCancelledByUser is published

## Failure Scenarios
- NotFound
- InvalidState (ACTIVE/COMPLETED)