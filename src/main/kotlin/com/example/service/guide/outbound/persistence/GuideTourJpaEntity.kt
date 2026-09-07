package com.example.service.guide.outbound.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Persistence row type for the `guide_tour` table.
 *
 * Timestamps are stored as `TIMESTAMP` without a zone and converted at UTC by
 * [GuideTourMapper]. Storing local time would make the stored value depend on the server's
 * zone, which turns a deployment detail into a data corruption.
 *
 * SDD: see `documentation/ports/guide-tour-repository.outport.spec.md`.
 */
@Entity
@Table(name = "guide_tour")
class GuideTourJpaEntity(
    @Id
    @Column(name = "id", nullable = false, length = 36)
    var id: String = "",
    @Column(name = "tour_id", nullable = false)
    var tourId: String = "",
    @Column(name = "scheduled_start", nullable = false)
    var scheduledStart: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "",
    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,
)
