# Port Specification – RequestTourBookingUseCase (Inport)

## Purpose

Defines the inbound application boundary for the UC01 – RequestTourBooking use case.
This inport is the only entry point for creating a new tour booking from the outside world.

SDD: See `documentation/use-cases/uc01-request-tour-booking.spec.md`


## 1. Interface

```
core.inport.usecase.RequestTourBookingUseCase
```

## 2. Method Contract

```
RequestTourBookingResult request(RequestTourBookingCommand command)
```

### 2.1 Input: RequestTourBookingCommand

| Field | Type | Constraint |
|---|---|---|
| tourId | String | non-null, non-blank |
| tourDate | LocalDate | non-null, must be in the future |
| participantCount | int | >= 1 |
| contactName | String | non-null, non-blank |
| contactEmail | String | non-null, non-blank |

### 2.2 Output: RequestTourBookingResult

| Field | Type | Description |
|---|---|---|
| bookingId | String | UUID string of the created booking |
| status | String | Always "REQUESTED" on success |

### 2.3 Exceptions

| Exception | Condition | HTTP mapping |
|---|---|---|
| `InvalidBookingRequestException` | tourDate is in the past, or participantCount < 1 | 400 |
| `CapacityExceededException` | participantCount exceeds available capacity | 409 |
| `AvailabilityUnavailableException` | AvailabilityChecker infrastructure failure | 502 |


## 3. Transaction Boundary

The transaction boundary is owned by the driver implementation (`RequestTourBookingDriver`).
The caller (REST controller) is transaction-unaware.


## 4. Implementation

Implementation class: `inbound.driver.RequestTourBookingDriver`


## 5. Constraints

- MUST NOT be called from within another transaction boundary.
- The inport interface MUST remain framework-free.
- Command and result types MUST NOT contain domain objects (aggregates, value objects).