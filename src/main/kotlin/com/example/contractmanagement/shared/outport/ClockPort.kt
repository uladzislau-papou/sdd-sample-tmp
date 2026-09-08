package com.example.contractmanagement.shared.outport

import java.time.Instant

/**
 * Cross-context outbound port providing the current point in time.
 *
 * The domain layer must never call `Instant.now()` directly. Drivers call this port and
 * pass the result as a parameter to aggregate methods, which keeps domain logic
 * deterministic and time-dependent invariants fully testable.
 *
 * Implementations live in `shared.outbound.clock` and are wired by
 * `bootstrap.SharedConfig`: an adapter for a shared port belongs to no bounded context
 * (`documentation/architecture.definition.md` section 9).
 *
 * SDD: see `documentation/ports/clock.outport.spec.md`.
 */
interface ClockPort {
    /** Returns the current moment in time. */
    fun now(): Instant
}
