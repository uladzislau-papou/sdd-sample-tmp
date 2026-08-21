package com.dominikgaller.alpinebooking.bootstrap;

import org.springframework.context.annotation.Configuration;

/**
 * Wiring configuration for the guide bounded context.
 *
 * <p>No new beans are declared here. {@code ClockPort} and {@code DomainEventPublisher}
 * are shared outports declared in {@link SharedConfig} — the guide context must not
 * obtain them from {@code BookingConfig}, which is how ADR-0003's independence claim
 * was quietly false before. Its own components are picked up by component scan:
 * {@code GuideTourJooqRepository} via {@code @Repository}, and the drivers
 * {@code StartTourDriver} (UC05) and {@code CompleteTourDriver} (UC11) via
 * {@code @Service}.
 *
 * <p>The class exists so the context has a declared wiring seam even while empty —
 * deleting it would move guide wiring back into a shared config the moment one bean
 * needs explicit construction.
 *
 * <p>SDD: See {@code documentation/adr/0003-separate-guide-bounded-context.adr.md}.
 */
@Configuration
public class GuideConfig {
}
