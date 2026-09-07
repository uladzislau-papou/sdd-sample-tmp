package com.example.service.guide.outbound.persistence

import com.example.service.guide.core.domain.guidetour.GuideTour
import com.example.service.guide.core.domain.guidetour.GuideTourId
import com.example.service.guide.core.outport.GuideTourRepository
import org.springframework.stereotype.Repository

/**
 * JPA-backed adapter for the [GuideTourRepository] outbound port.
 *
 * `save` and `update` both merge the whole entity, for the reason recorded on the booking
 * side's adapter.
 */
@Repository
internal class GuideTourJpaRepository(
    private val entityRepository: GuideTourJpaEntityRepository,
) : GuideTourRepository {
    private val mapper = GuideTourMapper()

    override fun save(guideTour: GuideTour) {
        entityRepository.save(mapper.toEntity(guideTour))
    }

    override fun findById(guideTourId: GuideTourId): GuideTour? =
        entityRepository
            .findById(guideTourId.value.toString())
            .map(mapper::toDomain)
            .orElse(null)

    override fun update(guideTour: GuideTour) {
        entityRepository.save(mapper.toEntity(guideTour))
    }
}
