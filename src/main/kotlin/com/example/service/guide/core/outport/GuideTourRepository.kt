package com.example.service.guide.core.outport

import com.example.service.guide.core.domain.guidetour.GuideTour
import com.example.service.guide.core.domain.guidetour.GuideTourId

/**
 * Outbound port for persisting the `GuideTour` aggregate.
 *
 * `update` must write every mutable field, for the reason recorded on the booking side's
 * equivalent port.
 *
 * SDD: see `documentation/ports/guide-tour-repository.outport.spec.md`.
 */
interface GuideTourRepository {
    fun save(guideTour: GuideTour)

    /** Returns the aggregate, or null when no tour has that identity. */
    fun findById(guideTourId: GuideTourId): GuideTour?

    fun update(guideTour: GuideTour)
}
