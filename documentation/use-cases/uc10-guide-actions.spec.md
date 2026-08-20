# Use Case Specification – Guide Actions (superseded)

## Status
SUPERSEDED

## Bounded Context
`guide` — see the successor specs.

## Purpose

This file originally stacked three guide-side use case sketches under the heading
`NOT SURE IF NECESSARY!`, written before UC05–UC09 existed. It has been split so
that each use case lives in its own file, as required by
`file-naming.definition.md` and the one-use-case-per-spec rule in
`use-case.spec.template.md`.

Kept for provenance only. **Not a `/loop-uc` target** — the loop refuses
`SUPERSEDED` specs.

## Successors

| Original block | Successor | Status |
|----------------|-----------|--------|
| StartTour (Guide) | [`uc05-start-tour.spec.md`](uc05-start-tour.spec.md) | IMPLEMENTED |
| CompleteTour (Guide) | [`uc11-complete-tour.spec.md`](uc11-complete-tour.spec.md) | SPECIFIED |
| CancelTourByGuide (Guide) | [`uc12-cancel-tour-by-guide.spec.md`](uc12-cancel-tour-by-guide.spec.md) | SPECIFIED |

The original note asked whether these were "still necessary after implementing
uc05 to uc09". The answer, recorded here so it is not lost again:

- **StartTour was necessary** and became UC05.
- **CompleteTour and CancelTourByGuide are still necessary**, and were not covered
  by UC07/UC09. Those two are the *booking-side* reactions
  (`MarkBookingCompleted`, `MarkBookingCancelledByGuide`); UC11 and UC12 are the
  *guide-side* actions that trigger them. Without UC11 there is no publisher for
  `TourCompleted`, so UC07 has no trigger at all — `GuideTour` currently exposes
  only `start(...)`.

## 1.–10.

Not applicable — content moved to the successor specs above.
