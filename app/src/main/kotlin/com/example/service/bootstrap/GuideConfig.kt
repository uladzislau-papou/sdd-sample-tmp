package com.example.service.bootstrap

import org.springframework.context.annotation.Configuration

/**
 * Wiring configuration for the `guide` bounded context.
 *
 * No beans are declared here yet. `ClockPort` and `DomainEventPublisher` are shared
 * outports declared in [SharedConfig] — this context must not obtain them from
 * [BookingConfig]. Its own components are picked up by component scan.
 *
 * The class exists so the context has a declared wiring seam even while empty. Deleting
 * it would move guide wiring back into a shared config the moment one bean needs
 * explicit construction.
 *
 * SDD: see `documentation/architecture.definition.md`, section 4.9.
 */
@Configuration
class GuideConfig
