# Use Case Specification – StartTour (Guide)

## Status
IMPLEMENTED

## Bounded Context
`guide` — triggered via REST by the guide. Publishes `TourStarted` to `shared.domain.event` as a cross-context integration event consumed by `booking`.

## Purpose
Start a scheduled guide tour execution.

## 1. Intent
Transition a scheduled GuideTour (or TourExecution) into RUNNING/ACTIVE on the tour day.

## 2. Input Contract
Fields:
- guideTourId
- startedAt (optional; default = now)

## 3. Output Contract
Return type:
- status

Error type(s):
- InvalidState
- TooEarly
- NotFound

## 4. Preconditions
- GuideTour exists
- GuideTour state = READY (or SCHEDULED)
- Current date/time >= tourStartTime (or within allowed start window)
- (Optional) Actor is authorized guide for guideId on the GuideTour

## 5. Flow
1. Load GuideTour aggregate (guideTourId)
2. Call start(startedAt)
3. Persist GuideTour
4. Publish TourStarted (Guide domain event)

## 6. Side Effects
- Persistence
- Event publication

## 7. Acceptance Criteria
Given a READY GuideTour on the tour day
When StartTour is executed
Then state becomes RUNNING (or ACTIVE)
And TourStarted is published

## 8. Failure Scenarios
- Invalid state (e.g. already RUNNING/FINISHED/CANCELLED)
- Too early (before start window)
- GuideTour not found

## 9. Test Requirements
Must include:
- Happy path
- Invalid state
- Early start test
- Not found test