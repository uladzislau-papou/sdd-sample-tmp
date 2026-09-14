package com.jobradleasing.contractmanagement.shared.outport

import java.time.Instant

/** Test double returning a fixed instant, never the system clock (test.definition.md § 4.3). */
class FixedClockPort(
    private val fixed: Instant,
) : ClockPort {
    override fun now(): Instant = fixed
}
