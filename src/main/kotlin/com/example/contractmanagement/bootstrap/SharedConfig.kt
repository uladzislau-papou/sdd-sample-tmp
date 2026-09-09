package com.example.contractmanagement.bootstrap

import com.example.contractmanagement.shared.outbound.clock.SystemClockPort
import com.example.contractmanagement.shared.outbound.integration.LoggingDomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wiring for the shared kernel's outbound adapters.
 *
 * These beans implement `shared.outport` interfaces, which more than one bounded context
 * depends on and none of them owns. They are declared here rather than in a per-context
 * config so that no context has to obtain a context-neutral bean from another context's
 * wiring.
 *
 * That is not hypothetical tidiness. `ClockPort` and `DomainEventPublisher` were once
 * declared in one bounded context's config, which meant a second context got its clock from
 * the first context's configuration. No import crossed a context boundary, so no compile-time
 * check could see it — but the claim that the two contexts depended only on `shared.domain`
 * was false.
 *
 * The service currently has **no** bounded contexts, so these two beans are the whole of its
 * wiring. That is a transient state — see
 * `documentation/adr/0026-the-empty-service-is-a-transient-state.adr.md`.
 *
 * SDD: see `documentation/architecture.definition.md` sections 9 and 4.9.
 */
@Configuration
class SharedConfig {
    @Bean
    fun domainEventPublisher(applicationEventPublisher: ApplicationEventPublisher) =
        LoggingDomainEventPublisher(applicationEventPublisher)

    @Bean
    fun clockPort() = SystemClockPort()
}
