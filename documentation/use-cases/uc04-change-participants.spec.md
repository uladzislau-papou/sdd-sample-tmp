# Use Case Specification – ChangeParticipants

## Status
SPECIFIED

## Purpose

Change participant count.


## 1. Intent

Update participant count while respecting availability.


## 2. Input Contract

Fields:
- bookingId
- newParticipantCount


Validation rules:
- Required
- newParticipantCount >= 1


## 3. Output Contract

Return type:
- updated participantCount

Error type(s):
- CapacityExceeded
- InvalidState


## 4. Preconditions

- Booking exists
- State = REQUESTED or CONFIRMED


## 5. Flow

1. Load aggregate
2. Check availability delta
3. Call changeParticipants()
4. Persist
5. Publish ParticipantsChanged


## 6. Side Effects

- Persistence
- Availability check
- Event publication


## 7. Acceptance Criteria

Given available capacity
When participant count increases
Then booking reflects new count


## 8. Failure Scenarios

- Capacity exceeded
- Invalid state


## 9. Test Requirements

Must include:
- Happy path
- Capacity violation
- Persistence verification