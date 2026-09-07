package com.example.service.shared.domain.event

import com.example.service.shared.domain.TourId
import java.time.Instant

/**
 * Integration event emitted by the `guide` bounded context when a guide tour is started.
 *
 * Lives in `shared.domain.event` so the `booking` context can subscribe without depending
 * on the `guide` package. `guideTourId` is carried as a plain [String] to avoid a
 * `shared -> guide` compile-time dependency.
 *
 * Immutable. Published after the guide tour's transaction commits (ADR 0002).
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md` section 6, and
 * `documentation/adr/0005-bounded-context-identity-boundaries.adr.md` for the
 * foreign-identity rule.
 */
data class TourStarted(
    val guideTourId: String,
    val tourId: TourId,
    val startedAt: Instant,
) : DomainEvent
