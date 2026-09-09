# Port Specification – ClockPort (Outport)

## Purpose

Provides the current point in time to the application layer.
Ensures the domain layer never calls `Instant.now()` directly, keeping it deterministic and testable.

SDD: See `documentation/architecture.definition.md` Section 8 – "Time, randomness, and 'now'".


## 1. Interface

```
shared.outport.ClockPort
```

Lives in `shared.outport`, not a context's `core.outport`: it is context-neutral
infrastructure that any context needs (`architecture.definition.md` § 9, § 11).

The service currently has one bounded context, which makes "more than one context needs it"
untestable today. It stays in `shared` regardless, because the test in § 9 is **ownership, not
usage**: no context owns the clock.

## 2. Method Contract

### 2.1 now

```
Instant now()
```

**Responsibility:** Return the current moment in time as an `Instant`.

**Preconditions:** None.

**Postconditions:**
- Returns a non-null `Instant` representing the current moment.
- The returned value is used by the application layer and passed into aggregate factory methods as a parameter.

**Exceptions:** None under normal circumstances. Infrastructure failures (clock source unavailable) are a system-level concern and not part of this contract.


## 3. Usage Context

The driver calls `clockPort.now()` before invoking any domain factory method that requires temporal validation.
The `Instant` is then passed as a parameter to the aggregate (e.g. `Master.create(..., now)`).

The domain MUST NOT call `Instant.now()` directly.


## 4. Reference Implementation

Class: `shared.outbound.clock.SystemClockPort`, wired by `bootstrap.SharedConfig`.

Behaviour: Returns `Instant.now()` (system clock, UTC). This is the only place in the
codebase permitted to call `Instant.now()` (`architecture.definition.md` § 8).

It once lived inside a bounded context's `outbound.integration.clock` and was wired by that
context's `@Configuration`, which meant a second context obtained its clock from the first
context's wiring — a cross-context dependency invisible to any import check
(`architecture.definition.md` § 9). That is why the adapter's location is a rule and not a
preference.


## 5. Test Usage

In unit tests, `ClockPort` is replaced by a stub returning a fixed `Instant`.
This allows deterministic testing of all time-dependent behaviour (e.g. UC01's AC-01, which asserts the Master's `createdAt` is the clock's value).


## 6. Constraints

- The interface MUST remain framework-free.
- Domain objects MUST NOT hold a reference to `ClockPort`.
- Time values MUST be passed as parameters, not injected into aggregates.