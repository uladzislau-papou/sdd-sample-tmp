package com.example.contractmanagement.guide.core.domain.guidetour

import java.util.UUID

/**
 * Identity of a [GuideTour].
 *
 * Owned by the `guide` context. The `booking` context receives it as an opaque [String] on
 * a domain event and never parses it — see
 * `documentation/adr/0005-bounded-context-identity-boundaries.adr.md`.
 *
 * SDD: see `documentation/domain/aggregate-guide-tour.spec.md`.
 */
data class GuideTourId(
    val value: UUID,
) {
    companion object {
        fun generate(): GuideTourId = GuideTourId(UUID.randomUUID())
    }
}
