package com.example.service.guide.inbound.rest.request

import java.time.Instant

/**
 * REST request body for UC05 — StartTour. Optional.
 *
 * @property startedAt when the tour actually began. Null, or an absent body entirely, means
 *   "now" and the driver reads `ClockPort`.
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md` § 9.
 */
data class StartTourRequest(
    val startedAt: Instant?,
)
