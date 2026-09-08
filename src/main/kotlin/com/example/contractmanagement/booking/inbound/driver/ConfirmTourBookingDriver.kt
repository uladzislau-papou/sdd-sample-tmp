package com.example.contractmanagement.booking.inbound.driver

import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.contractmanagement.booking.core.inport.command.ConfirmTourBookingCommand
import com.example.contractmanagement.booking.core.inport.result.ConfirmTourBookingResult
import com.example.contractmanagement.booking.core.inport.usecase.ConfirmTourBookingUseCase
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
import com.example.contractmanagement.shared.outport.ClockPort
import com.example.contractmanagement.shared.outport.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Application service implementing UC02 — ConfirmTourBooking.
 *
 * Owns the transaction boundary. Loads, transitions, persists, hands off events.
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md`.
 */
@Service
@Transactional
class ConfirmTourBookingDriver(
    private val tourBookingRepository: TourBookingRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : ConfirmTourBookingUseCase {
    override fun confirm(command: ConfirmTourBookingCommand): ConfirmTourBookingResult {
        val bookingId = BookingId.of(UUID.fromString(command.bookingId))
        val now = clockPort.now()

        val booking =
            tourBookingRepository.findById(bookingId)
                ?: throw BookingNotFoundException(command.bookingId)

        booking.confirm(now)

        tourBookingRepository.update(booking)
        booking.pullDomainEvents().forEach(domainEventPublisher::publish)

        return ConfirmTourBookingResult(booking.status.name)
    }
}
