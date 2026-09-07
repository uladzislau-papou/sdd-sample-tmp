package com.example.service.guide.outbound.persistence

import com.example.service.guide.core.domain.guidetour.GuideTour
import com.example.service.guide.core.domain.guidetour.GuideTourId
import com.example.service.guide.core.domain.guidetour.GuideTourStatus
import com.example.service.shared.domain.TourId
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Pure mapping between the [GuideTour] aggregate and [GuideTourJpaEntity].
 *
 * `Instant` is converted to and from `LocalDateTime` at UTC, matching the booking side. A
 * null `startedAt` stays null: a tour that has not started must remain distinguishable
 * from one that started at the epoch.
 */
internal class GuideTourMapper {
    fun toEntity(guideTour: GuideTour) =
        GuideTourJpaEntity(
            id = guideTour.id.value.toString(),
            tourId = guideTour.tourId.value,
            scheduledStart = LocalDateTime.ofInstant(guideTour.scheduledStart, ZoneOffset.UTC),
            status = guideTour.status.name,
            startedAt = guideTour.startedAt?.let { LocalDateTime.ofInstant(it, ZoneOffset.UTC) },
        )

    fun toDomain(entity: GuideTourJpaEntity) =
        GuideTour.reconstitute(
            id = GuideTourId(UUID.fromString(entity.id)),
            tourId = TourId(entity.tourId),
            scheduledStart = entity.scheduledStart.toInstant(ZoneOffset.UTC),
            status = GuideTourStatus.valueOf(entity.status),
            startedAt = entity.startedAt?.toInstant(ZoneOffset.UTC),
        )
}
