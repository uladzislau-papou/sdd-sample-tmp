# Port Specification – AvailabilityChecker (Outport)

## Purpose

Defines the boundary for checking available capacity for a given tour and date.
This outport abstracts external or in-process availability logic from the domain.

The aggregate enforces the capacity invariant.
This port only provides the data required to do so.

SDD: See `documentation/use-cases/uc01-request-tour-booking.spec.md` (Flow Step 2)


## 1. Interface

```
core.outport.AvailabilityChecker
```

## 2. Method Contract

### 2.1 checkAvailability

```
AvailableCapacity checkAvailability(TourId tourId, TourDate tourDate)
```

**Responsibility:** Return the number of available spots for the given tour and date.

**Preconditions:**
- `tourId` must be non-null.
- `tourDate` must be non-null.

**Postconditions:**
- Returns `AvailableCapacity` representing the number of open spots.
- The returned value is a snapshot at the time of the call; no reservation is made.

**Exceptions:**

| Exception | Condition |
|---|---|
| `AvailabilityUnavailableException` | Infrastructure failure – the availability source cannot be reached or returns an invalid response. |

**Capacity enforcement:** This port does NOT throw on capacity exceeded.
The `TourBooking` aggregate enforces the invariant `participantCount <= availableCapacity`.


## 3. Transaction Boundary

This port is called within the active transaction of the driver.
Implementations MUST NOT start their own transaction.
Implementations SHOULD be read-only (no side effects).


## 4. Reference Implementation

Class: `outbound.integration.StubAvailabilityChecker`

Behaviour: Always returns `AvailableCapacity(Integer.MAX_VALUE)`.
Intent: Allows the system to function without an external availability source in the reference implementation.


## 5. Constraints

- MUST NOT contain business rules (e.g., do not reject low capacity here).
- MUST NOT access `TourBookingRepository` or any write-side persistence.
- Parameters and return type MUST be domain value objects only.