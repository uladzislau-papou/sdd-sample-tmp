# Use Case Specification – ConfirmTourBooking

## Status
SPECIFIED

## Purpose

Confirm an existing tour booking.


## 1. Intent

Transition booking from REQUESTED to CONFIRMED.


## 2. Input Contract

Fields:
- bookingId

Validation rules:
- Required
- Format valid


## 3. Output Contract

Return type:
- status

Error type(s):
- NotFound
- InvalidState


## 4. Preconditions

- Booking must exist
- State must be REQUESTED


## 5. Flow

1. Load aggregate
2. Call confirm()
3. Persist
4. Publish TourBookingConfirmed


## 6. Side Effects

- Persistence
- Event publication


## 7. Acceptance Criteria

Given a requested booking
When confirm is executed
Then booking is CONFIRMED


## 8. Failure Scenarios

- Aggregate not found
- Invalid state transition


## 9. Test Requirements

Must include:
- Happy path test
- Invalid state test
- Persistence verification