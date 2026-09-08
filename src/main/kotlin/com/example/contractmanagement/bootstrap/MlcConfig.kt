package com.example.contractmanagement.bootstrap

import org.springframework.context.annotation.Configuration

/**
 * Wiring configuration for the `mlc` bounded context.
 *
 * No beans are declared here yet. `ClockPort` and `DomainEventPublisher` are shared outports
 * declared in [SharedConfig] — this context must not obtain them from another context's
 * wiring, which is the mistake `SharedConfig`'s KDoc records: they once lived in booking's
 * config, so the guide context got its clock from booking's configuration and no import
 * crossed a boundary, so no compile-time check could see it.
 *
 * `MasterLeasingContractJpaRepository` and the GraphQL resolver are picked up by component
 * scan. UC07 declares no outport of its own beyond persistence and the clock: **PD-03** keeps
 * `partnerNumber` an input rather than something fetched, and **PD-06** leaves the employer's
 * existence unverified — either decision reversing would put the first foreign-system adapter
 * in this file.
 *
 * The class exists so the context has a declared wiring seam even while empty. Deleting it
 * would move `mlc` wiring back into a shared config the moment one bean needs explicit
 * construction.
 *
 * SDD: see `documentation/architecture.definition.md`, section 4.9.
 */
@Configuration
class MlcConfig
