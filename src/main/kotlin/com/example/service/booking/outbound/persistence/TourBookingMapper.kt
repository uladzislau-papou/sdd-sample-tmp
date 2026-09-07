package com.example.service.booking.outbound.persistence

import com.example.service.booking.core.domain.tourbooking.AvailableCapacity
import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.ParticipantContact
import com.example.service.booking.core.domain.tourbooking.ParticipantCount
import com.example.service.booking.core.domain.tourbooking.TourBooking
import com.example.service.booking.core.domain.tourbooking.TourBookingStatus
import com.example.service.booking.core.domain.tourbooking.TourDate
import com.example.service.shared.domain.TourId
import java.util.UUID

/**
 * Pure mapping between the [TourBooking] aggregate and [TourBookingJpaEntity].
 *
 * No Spring, no IO. Reconstitution goes through `TourBooking.reconstitute`, which is why
 * `ClassRoleRulesTest` permits that call from `..outbound.persistence..` and from nowhere
 * else: any other caller would be skipping the invariants `request` enforces.
 */
internal class TourBookingMapper {
    fun toEntity(booking: TourBooking) =
        TourBookingJpaEntity(
            id = booking.bookingId.value.toString(),
            tourId = booking.tourId.value,
            tourDate = booking.tourDate.value,
            participantCount = booking.participantCount.value,
            availableCapacity = booking.availableCapacity.value,
            contactName = booking.contact.name,
            contactEmail = booking.contact.email,
            status = booking.status.name,
        )

    fun toDomain(entity: TourBookingJpaEntity) =
        TourBooking.reconstitute(
            bookingId = BookingId.of(UUID.fromString(entity.id)),
            tourId = TourId(entity.tourId),
            tourDate = TourDate(entity.tourDate),
            participantCount = ParticipantCount(entity.participantCount),
            availableCapacity = AvailableCapacity(entity.availableCapacity),
            contact = ParticipantContact(entity.contactName, entity.contactEmail),
            status = TourBookingStatus.valueOf(entity.status),
        )
}
