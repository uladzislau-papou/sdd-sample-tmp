# Use Case Specification – CancelTourBooking

## Status
SPECIFIED

## Purpose

Cancel a booking.


## 1. Intent

Transition booking to CANCELLED.


## 2. Input Contract

Fields:
- bookingId

Validation rules:
- Required


## 3. Output Contract

Return type:
- status

Error type(s):
- NotFound
- InvalidState


## 4. Preconditions

- Booking must exist
- State must allow cancellation


## 5. Flow

1. Load aggregate
2. Call cancel(now)
3. Persist
4. Publish TourBookingCancelled


## 6. Side Effects

- Persistence
- Event publication


## 7. Acceptance Criteria

Given a confirmed booking
When cancel is executed
Then booking is CANCELLED


## 8. Failure Scenarios

- Invalid state
- Aggregate not found


## 9. Test Requirements

Must include:
- Happy path
- Invalid state
- Event verification