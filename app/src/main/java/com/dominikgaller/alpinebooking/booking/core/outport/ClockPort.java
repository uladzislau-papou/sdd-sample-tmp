package com.dominikgaller.alpinebooking.booking.core.outport;

import java.time.Instant;

/**
 * Outbound port providing the current point in time.
 *
 * <p>The domain layer must never call {@code Instant.now()} directly.
 * The driver calls this port and passes the result as a parameter to aggregate factory methods.
 * This keeps domain logic deterministic and time-dependent invariants fully testable.
 *
 * <p>Framework-free: implementations live in {@code outbound.integration.clock}.
 *
 * <p>SDD: See {@code documentation/ports/clock.outport.spec.md}
 *          and {@code documentation/architecture.definition.md}, section 8.
 */
public interface ClockPort {

    /**
     * Returns the current moment in time.
     *
     * @return a non-null {@link Instant} representing now
     */
    Instant now();
}
