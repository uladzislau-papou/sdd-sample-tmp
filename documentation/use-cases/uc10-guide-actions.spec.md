# NOT SURE IF NECESSARY!
---

Not sure whether the following use case specifications are still necessary 
after implementing uc05 to uc09. Nevertheless, for purposes of completeness
they are prepared here so that they are not forgotten and can be easily 
executed if missing.

--- 

# Use Case Specification – StartTour (GuideOperations)

## Status
SPECIFIED

## Purpose
Start a scheduled guide tour execution.

## Input Contract
Fields:
- guideTourId
- startedAt (optional; default = now)

## Preconditions
- GuideTour exists
- GuideTour status = READY (or SCHEDULED)

## Flow
1. Load GuideTour (guideTourId)
2. guideTour.start(startedAt)
3. Persist GuideTour
4. Publish TourStarted

## Side Effects
- Persistence
- Event publication (TourStarted)

---

# Use Case Specification – CompleteTour (GuideOperations)

## Status
SPECIFIED

## Purpose
Complete a running guide tour execution.

## Input Contract
Fields:
- guideTourId
- completedAt (optional; default = now)

## Preconditions
- GuideTour exists
- GuideTour status = RUNNING

## Flow
1. Load GuideTour
2. guideTour.complete(completedAt)
3. Persist GuideTour
4. Publish TourCompleted

--- 

# Use Case Specification – CancelTourByGuide (GuideOperations)

## Status
SPECIFIED

## Purpose
Cancel a scheduled or running guide tour execution by the guide.

## Input Contract
Fields:
- guideTourId
- cancelledAt (optional; default = now)
- reason (optional)

## Preconditions
- GuideTour exists
- GuideTour status in {READY, RUNNING}

## Flow
1. Load GuideTour
2. guideTour.cancel(cancelledAt, reason)
3. Persist GuideTour
4. Publish TourCancelledByGuide