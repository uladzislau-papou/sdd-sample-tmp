# Port Specification – ClockPort (Outport)

## Purpose

Provides the current point in time to the application layer.
Ensures the domain layer never calls `Instant.now()` directly, keeping it
deterministic and testable.

SDD: See `documentation/architecture.definition.md` § 8 — "Time, randomness, and
'now'".


## 1. Interface

```
shared.outport.ClockPort
```

Lives in `shared.outport`, not a context's `core.outport`: both `masterleasing` and
`individualleasing` need it, so it is shared-kernel infrastructure
(`architecture.definition.md` § 9, § 11).


## 2. Method Contract

### 2.1 now

```kotlin
fun now(): Instant
```

**Responsibility:** Return the current moment as an `Instant`.

**Preconditions:** None.

**Postconditions:**
- Returns a non-null `Instant`. The Kotlin return type says so; there is no
  `Optional` and no nullability to document (`coding-style.definition.md` § 1.4).
- The returned value is passed into aggregate functions as a parameter, never
  injected into them.

**Exceptions:** None under normal circumstances.

### 2.2 Why there is no `today()`

This domain is more date-driven than instant-driven: `activation_date`,
`cancelled_date`, `term_start`, `term_end` and `first_due_date` are all calendar
dates. A `fun today(): LocalDate` would be the obvious companion and is deliberately
absent.

Every business date in this system is **supplied by the caller**, not observed
(`architecture.definition.md` § 8.1). An activation date is what the parties agreed,
routinely backdated to the start of a month; a `termStart` is a commercial term. A
`today()` on this port would exist only to be a default for one of them, and a
defaulted business date is a date nobody agreed.

If a use case ever genuinely needs the current calendar date — a scheduled job
moving contracts to `ENDED`, say — it needs a time zone as well, because "today"
differs by one across a zone boundary, and that is a decision worth making
explicitly rather than inheriting from a JVM default.


## 3. Usage Context

A driver calls `clockPort.now()` before invoking any domain function that records
when something happened. The `Instant` is then passed as a parameter, e.g.
`contract.cancel(cancelledDate, reason, now)`.

The domain MUST NOT call `Instant.now()`, `LocalDate.now()` or
`LocalDateTime.now()` directly. `ClassRoleRulesTest` fails the build on any such
call outside the reference implementation (ADR 0007).

**A driver behind a GraphQL mutation reads from this port and never from the
request** (`architecture.definition.md` § 8.1). A driver reached from a listener or
from another context's inport uses the timestamp it was given, falling back here
only when none was supplied.


## 4. Reference Implementation

`shared.outbound.clock.SystemClockPort`, wired by `bootstrap.SharedConfig`.

Behaviour: returns `Instant.now()` (system clock, UTC). **This is the only place in
the codebase permitted to call `Instant.now()`.**

It lives in `shared.outbound` rather than inside a bounded context because an
implementation of a shared port is owned by no context either. Putting it in
`masterleasing.outbound` would force `individualleasing` to obtain a clock from
`MasterLeasingConfig` — a cross-context dependency invisible to any import check
(`architecture.definition.md` § 9).


## 5. Test Usage

In tests, `ClockPort` is replaced by a stub returning a **fixed** `Instant`.

This is not optional. `test.definition.md` § 4.3 forbids a test reading the current
time, and this domain is the reason: every invariant that compares dates — I-08 on
the master contract, I-06 on the lease — produces a suite that passes until a term
boundary, a month end, or a leap day.

A stub is a two-line object; no mocking framework is available and none is needed
(`technical.spec.md`, Testing).


## 6. Constraints

- The interface MUST remain framework-free.
- Domain objects MUST NOT hold a reference to `ClockPort`.
- Time values MUST be passed as parameters, not injected into aggregates.
- The port MUST NOT gain a time zone, a `Clock`, or a `today()` without an ADR — a
  wider time abstraction is a cross-cutting concern (`sdd.playbook.md` § 6 item 11)
  and § 2.2 records why the obvious addition is not wanted.
