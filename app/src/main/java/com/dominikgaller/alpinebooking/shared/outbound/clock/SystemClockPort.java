package com.dominikgaller.alpinebooking.shared.outbound.clock;

import com.dominikgaller.alpinebooking.shared.outport.ClockPort;

import java.time.Instant;

/**
 * Production implementation of {@link ClockPort} that delegates to {@link Instant#now()}.
 *
 * <p>SDD: See {@code documentation/ports/clock.outport.spec.md}.
 */
public class SystemClockPort implements ClockPort {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
