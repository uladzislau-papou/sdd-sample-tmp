# ADR 0003 – Separate Guide Bounded Context

## Status
**Withdrawn** — the `guide` bounded context and the whole tour-booking example were deleted
from the tree. This ADR decided a boundary between two contexts that no longer exist.

Kept rather than deleted, because its *reasoning* is the clearest worked example this
repository has of the argument a new-context ADR is supposed to make, and `adr/0005` cites
it. `adr/0024` is now the live precedent for "a new bounded context requires an ADR".

## Context

UC05 (StartTour) was implemented with its `GuideTour` aggregate placed inside the `booking`
bounded context package (`com.dominikgaller.alpinebooking.booking`). This was a structural
shortcut that violates the bounded-context model defined in `architecture.definition.md`.

The evidence that `GuideTour` does not belong in `booking`:

1. **UC05 spec title** names the context explicitly: *"StartTour (Guide)"*.
2. **UC06** treats `guideTourId` as a plain string correlation ID — `booking` does not load,
   validate, or depend on any `GuideTour` type. This is the classic cross-context reference
   pattern: one context holds the ID, the other owns the aggregate.
3. **Domain language**: `GuideTour` models the *guide's execution record* of a tour.
   `TourBooking` models the *customer's reservation*. These are different responsibilities,
   different lifecycles, different invariants.
4. **Coupling smell**: `BookingExceptionHandler` currently handles `GuideTourNotFoundException`,
   `InvalidGuideTourStateException`, and `TourStartTooEarlyException` — exceptions that have
   nothing to do with booking.

## Decision

**Create a new `guide` bounded context** at
`com.dominikgaller.alpinebooking.guide`, and move all guide-tour-related code into it.

**Move `TourId` to `shared.domain`**, since it is a cross-context identifier referenced by
both `booking` (on `TourBooking`) and `guide` (on `GuideTour`). The `shared` package
is the correct home for genuinely cross-context building blocks (see `architecture.definition.md`,
section 10).

The resulting package structure:

```
com.dominikgaller.alpinebooking
├── bootstrap
│   ├── AlpineBookingApplication
│   ├── BookingConfig
│   └── GuideConfig                    ← new
├── shared
│   └── domain
│       ├── event
│       │   └── DomainEvent
│       └── TourId                     ← moved from booking.core.domain
├── booking                            ← TourBooking and its ports only
│   └── core / inbound / outbound / …
└── guide                              ← new bounded context
    └── core / inbound / outbound / …
```

## Rationale

### Why a separate bounded context?

Bounded contexts exist to keep models consistent and independently evolvable. `GuideTour` and
`TourBooking` have different owners (guide operations team vs. booking team in a real system),
different lifecycles, and different invariants. Mixing them forces artificial coupling.

### Why move `TourId` to `shared`?

Both contexts reference the same logical concept (a tour from the catalog). Duplicating the type
in each context would create structural identity mismatch. Keeping it in `booking` would force
`guide` to depend on `booking`, violating the independence rule. `shared.domain` is
the correct home: framework-free, minimal, cross-context by design.

### Why not a full anti-corruption layer?

In a production system, inter-context references would use integration events or ACL mappers.
For this reference project the shared `TourId` value object is sufficient — it carries no
behaviour, only an identifier, and does not leak domain logic across the boundary.

## Consequences

- All `GuideTour*` classes move to `guide.*` packages.
- `TourId` moves to `shared.domain.TourId`; all import statements in `booking` and
  `guide` are updated accordingly.
- `BookingExceptionHandler` loses all `GuideTour*` exception handlers; a new
  `GuideExceptionHandler` handles them.
- `BookingConfig` remains unchanged; a new `GuideConfig` wires
  `guide`-specific beans (ClockPort and DomainEventPublisher are shared Spring beans
  already present — no new adapter implementations needed).
- Tests are co-located with the context they test.
- The `booking` context has zero compile-time dependencies on `guide`.
- `guide` has zero compile-time dependencies on `booking`.
- Both contexts depend on `shared.domain` only.
