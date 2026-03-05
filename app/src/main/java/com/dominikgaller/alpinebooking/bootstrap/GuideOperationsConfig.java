package com.dominikgaller.alpinebooking.bootstrap;

import org.springframework.context.annotation.Configuration;

/**
 * Wiring configuration for the guideoperations bounded context.
 *
 * <p>No new beans are declared here: {@link ClockPort} and {@link DomainEventPublisher}
 * are shared outports declared in {@link BookingConfig}; {@link GuideTourJooqRepository}
 * and {@link StartTourDriver} are annotated with {@code @Repository} and {@code @Service}
 * respectively and are picked up by component scan.
 *
 * <p>SDD: See {@code documentation/adr/0003-separate-guideoperations-bounded-context.adr.md}.
 */
@Configuration
public class GuideOperationsConfig {
}
