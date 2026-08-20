package com.dominikgaller.alpinebooking.shared.outport;

import java.time.Instant;

/**
 * Cross-context outbound port providing the current point in time.
 *
 * <p>The domain layer must never call {@code Instant.now()} directly.
 * Drivers call this port and pass the result as a parameter to aggregate methods.
 * This keeps domain logic deterministic and time-dependent invariants fully testable.
 *
 * <p>Used by both the {@code booking} and {@code guide} bounded contexts.
 *
 * <p>Framework-free: implementations live in {@code outbound.integration.clock}.
 *
 * <p>SDD: See {@code documentation/ports/clock.outport.spec.md},
 *          {@code documentation/architecture.definition.md} section 8,
 *          and {@code documentation/adr/0003-separate-guide-bounded-context.adr.md}.
 */
public interface ClockPort {

    /**
     * Returns the current moment in time.
     *
     * @return a non-null {@link Instant} representing now
     */
    Instant now();
}
