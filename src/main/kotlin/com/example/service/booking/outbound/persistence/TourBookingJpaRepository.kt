package com.example.service.booking.outbound.persistence

import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.TourBooking
import com.example.service.booking.core.domain.tourbooking.TourBookingStatus
import com.example.service.booking.core.outport.TourBookingRepository
import com.example.service.shared.domain.TourId
import org.springframework.stereotype.Repository

/**
 * JPA-backed adapter for the [TourBookingRepository] outbound port.
 *
 * **On `save` and `update` doing the same thing.** They do, and that is the point rather
 * than an oversight. The port keeps them separate because the callers' intents differ, and
 * a future adapter may need to act on that difference. Here both go through JPA's merge,
 * which writes the whole row.
 *
 * That closes a real defect class by construction. The hand-written SQL this replaced
 * updated only the columns its author remembered, which once discarded an entire use
 * case's effect while every test stayed green — each test asserted only the field it cared
 * about (`documentation/ports/tour-booking-repository.outport.spec.md` § 2.3). A rule
 * saying "write every mutable field" needs a reviewer to notice when it is broken; writing
 * the whole entity cannot be partially forgotten.
 */
@Repository
internal class TourBookingJpaRepository(
    private val entityRepository: TourBookingJpaEntityRepository,
) : TourBookingRepository {
    private val mapper = TourBookingMapper()

    override fun save(booking: TourBooking) {
        entityRepository.save(mapper.toEntity(booking))
    }

    override fun findById(bookingId: BookingId): TourBooking? =
        entityRepository
            .findById(bookingId.value.toString())
            .map(mapper::toDomain)
            .orElse(null)

    override fun findConfirmedByTourId(tourId: TourId): List<TourBooking> =
        entityRepository
            .findAllByTourIdAndStatus(tourId.value, TourBookingStatus.CONFIRMED.name)
            .map(mapper::toDomain)

    override fun update(booking: TourBooking) {
        entityRepository.save(mapper.toEntity(booking))
    }
}
