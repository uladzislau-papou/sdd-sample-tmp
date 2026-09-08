package com.example.contractmanagement.booking.outbound.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository over [TourBookingJpaEntity].
 *
 * `internal` on purpose: it is an implementation detail of [TourBookingJpaRepository], and
 * the rest of the service must reach persistence only through the outbound port. A
 * `JpaRepository` leaking into a driver would put paging, `Example` queries and entity
 * types into the core.
 */
internal interface TourBookingJpaEntityRepository : JpaRepository<TourBookingJpaEntity, String> {
    fun findAllByTourIdAndStatus(
        tourId: String,
        status: String,
    ): List<TourBookingJpaEntity>
}
