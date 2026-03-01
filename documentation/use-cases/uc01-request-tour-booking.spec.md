# Use Case Specification – RequestTourBooking

## Purpose

Create a new tour booking request.


## 1. Intent

Creates a TourBooking in REQUESTED state after validating availability.


## 2. Input Contract

Fields:
- tourId
- tourDate
- participantCount
- participantContact

Validation rules:
- All fields required
- tourDate format valid
- participantCount >= 1


## 3. Output Contract

Return type:
- bookingId
- status

Error type(s):
- CapacityExceeded
- InvalidInput


## 4. Preconditions

- Availability must confirm free capacity


## 5. Flow

1. Validate input (syntactic)
2. Call AvailabilityChecker (outport)
3. Create TourBooking aggregate
4. Persist aggregate
5. Publish TourBookingRequested


## 6. Side Effects

- Persistence
- Availability check
- Domain event publication


## 7. Acceptance Criteria

Given available capacity
When booking is requested
Then booking is stored in REQUESTED state


## 8. Failure Scenarios

- Capacity exceeded
- Invalid participant count
- External availability failure


## 9. Test Requirements

Must include:
- Happy path test
- Capacity exceeded test
- Persistence interaction verification