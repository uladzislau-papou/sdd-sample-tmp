# Port Specification – TourBookingRepository (Outport)

## Purpose

Defines the persistence boundary for the `TourBooking` aggregate on the write side.
This outport abstracts the storage mechanism from the domain and application layer.

SDD: See `documentation/domain/aggregate-tour-booking.spec.md`


## 1. Interface

```
core.outport.TourBookingRepository
```

## 2. Method Contracts

### 2.1 save

```
void save(TourBooking booking)
```

**Responsibility:** Persist a new `TourBooking` aggregate atomically.

**Preconditions:**
- `booking` must be non-null.
- `booking` must be in a valid, always-valid state.

**Postconditions:**
- All fields of the aggregate are durably persisted.
- The operation is atomic – either all fields are written or none are.

**Exceptions:**
- May throw unchecked infrastructure exceptions on persistence failure (e.g., constraint violation, DB unavailability). These are not part of the domain contract and are handled at the application boundary.

**Idempotency:** Not required. Saving an aggregate with a duplicate `bookingId` is a programming error (BookingId is generated before calling save).

**Scope for UC01:** Only `save` is required. No `update`, `findById`, or `delete` methods are needed for this use case.


## 3. Transaction Boundary

The transaction is owned by the driver (`RequestTourBookingDriver`).
The repository implementation MUST NOT start its own transaction.
The implementation participates in the active transaction.


## 4. Implementation

Implementation class: `outbound.persistence.write.TourBookingJooqRepository`

Technology: jOOQ with H2 (dev/test). Schema managed by Flyway.


## 5. Constraints

- MUST NOT expose jOOQ records, JPA entities, or any persistence types through this interface.
- MUST NOT load partial aggregates.
- Parameters and return types MUST be domain types only.