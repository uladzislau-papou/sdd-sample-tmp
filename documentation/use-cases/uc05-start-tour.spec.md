# Use Case Specification – StartTour

## Status
SPECIFIED

## Purpose

Mark tour as started.


## 1. Intent

Transition CONFIRMED booking to ACTIVE.


## 2. Input Contract

Fields:
- bookingId


## 3. Output Contract

Return type:
- status

Error type(s):
- InvalidState


## 4. Preconditions

- Booking exists
- State = CONFIRMED
- Current date >= tourDate


## 5. Flow

1. Load aggregate
2. Call startTour(now)
3. Persist
4. Publish TourStarted


## 6. Side Effects

- Persistence
- Event publication


## 7. Acceptance Criteria

Given confirmed booking on tour date
When startTour executed
Then state becomes ACTIVE


## 8. Failure Scenarios

- Invalid state
- Too early


## 9. Test Requirements

Must include:
- Happy path
- Invalid state
- Early start test