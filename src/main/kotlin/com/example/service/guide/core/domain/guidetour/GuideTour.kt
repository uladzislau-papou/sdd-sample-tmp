package com.example.service.guide.core.domain.guidetour

import com.example.service.guide.core.domain.guidetour.exception.InvalidGuideTourStateException
import com.example.service.guide.core.domain.guidetour.exception.TourStartTooEarlyException
import com.example.service.shared.domain.TourId
import com.example.service.shared.domain.event.DomainEvent
import com.example.service.shared.domain.event.TourStarted
import java.time.Instant

/**
 * Aggregate root representing a scheduled guide tour execution.
 *
 * The guide-side record of a tour session. It is independent of the `booking` context's
 * `TourBooking` aggregates, which are the customer-side reservations. The two learn about
 * each other only through a domain event in `shared.domain.event` — which is what lets
 * `ContextRegistryTest` forbid either context from importing the other's internals.
 *
 * State changes go through named transition methods; each records its event, handed off by
 * [pullDomainEvents].
 *
 * Framework-free: no Spring, no JPA, no IO.
 *
 * SDD: see `documentation/domain/aggregate-guide-tour.spec.md` and
 * `documentation/use-cases/uc05-start-tour.spec.md`.
 */
class GuideTour private constructor(
    val id: GuideTourId,
    val tourId: TourId,
    val scheduledStart: Instant,
    status: GuideTourStatus,
    startedAt: Instant?,
) {
    var status: GuideTourStatus = status
        private set

    /**
     * When the tour actually began. Null while it is still `SCHEDULED` — absence means
     * "not started yet", which is a real state rather than missing data.
     */
    var startedAt: Instant? = startedAt
        private set

    private val domainEvents = mutableListOf<DomainEvent>()

    /**
     * Transitions the tour from `SCHEDULED` to `RUNNING` (UC05).
     *
     * Publishes [TourStarted], which is what the `booking` context reacts to in UC06. The
     * identity is carried as a [String] deliberately: the event lives in `shared`, and
     * `shared` may not depend on this context (ADR-0005).
     *
     * @throws InvalidGuideTourStateException if the tour is not `SCHEDULED`
     * @throws TourStartTooEarlyException if [startedAt] precedes [scheduledStart]
     */
    fun start(startedAt: Instant) {
        if (status != GuideTourStatus.SCHEDULED) {
            throw InvalidGuideTourStateException(status)
        }
        if (startedAt.isBefore(scheduledStart)) {
            throw TourStartTooEarlyException(scheduledStart, startedAt)
        }
        status = GuideTourStatus.RUNNING
        this.startedAt = startedAt
        domainEvents += TourStarted(id.value.toString(), tourId, startedAt)
    }

    /** Returns and clears the recorded domain events; a second call returns an empty list. */
    fun pullDomainEvents(): List<DomainEvent> {
        val snapshot = domainEvents.toList()
        domainEvents.clear()
        return snapshot
    }

    companion object {
        /** Creates a tour in `SCHEDULED` state, with no pending events. */
        fun schedule(
            id: GuideTourId,
            tourId: TourId,
            scheduledStart: Instant,
        ): GuideTour = GuideTour(id, tourId, scheduledStart, GuideTourStatus.SCHEDULED, null)

        /**
         * Rebuilds a tour from its persisted state, with no pending events.
         *
         * Called by the persistence mapper; `ClassRoleRulesTest` enforces that.
         */
        fun reconstitute(
            id: GuideTourId,
            tourId: TourId,
            scheduledStart: Instant,
            status: GuideTourStatus,
            startedAt: Instant?,
        ): GuideTour = GuideTour(id, tourId, scheduledStart, status, startedAt)
    }
}
