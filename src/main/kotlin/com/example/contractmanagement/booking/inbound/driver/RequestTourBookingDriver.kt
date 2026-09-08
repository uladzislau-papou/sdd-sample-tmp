package com.example.contractmanagement.booking.inbound.driver

import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantContact
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantCount
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBooking
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.inport.result.RequestTourBookingResult
import com.example.contractmanagement.booking.core.inport.usecase.RequestTourBookingUseCase
import com.example.contractmanagement.booking.core.outport.AvailabilityChecker
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.shared.outport.ClockPort
import com.example.contractmanagement.shared.outport.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Application service implementing UC01 — RequestTourBooking.
 *
 * Owns the transaction boundary. Orchestrates mapping, the availability lookup, aggregate
 * creation, persistence and event hand-off — and contains **no** domain rule of its own.
 * Every decision below is either a mapping or a delegation; the capacity check and the
 * date check live in the aggregate, where a domain test can reach them.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md`.
 */
@Service
@Transactional
class RequestTourBookingDriver(
    private val tourBookingRepository: TourBookingRepository,
    private val availabilityChecker: AvailabilityChecker,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : RequestTourBookingUseCase {
    override fun request(command: RequestTourBookingCommand): RequestTourBookingResult {
        val tourId = TourId(command.tourId)
        val tourDate = TourDate(command.tourDate)
        val participantCount = ParticipantCount(command.participantCount)
        val contact = ParticipantContact(command.contactName, command.contactEmail)
        val bookingId = BookingId.generate()
        val now = clockPort.now()

        val availableCapacity = availabilityChecker.checkAvailability(tourId, tourDate)

        val booking =
            TourBooking.request(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = tourDate,
                participantCount = participantCount,
                availableCapacity = availableCapacity,
                contact = contact,
                now = now,
            )

        tourBookingRepository.save(booking)
        booking.pullDomainEvents().forEach(domainEventPublisher::publish)

        return RequestTourBookingResult(
            bookingId = bookingId.value.toString(),
            status = booking.status.name,
        )
    }
}
