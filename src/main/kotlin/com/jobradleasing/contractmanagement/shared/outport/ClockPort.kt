package com.jobradleasing.contractmanagement.shared.outport

import java.time.Instant

/**
 * Provides the current point in time to the application layer.
 *
 * SDD: See `documentation/ports/clock.outport.spec.md`.
 */
interface ClockPort {
    /** @return the current instant. Passed into aggregate functions as a parameter, never injected into them. */
    fun now(): Instant
}
