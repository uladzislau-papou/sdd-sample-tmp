package com.example.service.shared.outbound.clock

import com.example.service.shared.outport.ClockPort
import java.time.Instant

/**
 * Production implementation of [ClockPort] that delegates to [Instant.now].
 *
 * SDD: see `documentation/ports/clock.outport.spec.md`.
 */
class SystemClockPort : ClockPort {
    override fun now(): Instant = Instant.now()
}
