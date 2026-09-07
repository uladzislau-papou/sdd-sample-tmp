package com.example.service.booking.inbound.driver

import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.service.booking.core.inport.command.MarkBookingActiveCommand
import com.example.service.booking.core.inport.result.MarkBookingActiveResult
import com.example.service.booking.core.inport.usecase.MarkBookingActiveUseCase
import com.example.service.booking.core.outport.TourBookingRepository
import com.example.service.shared.outport.ClockPort
import com.example.service.shared.outport.DomainEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Application service implementing UC06 — MarkBookingActive.
 *
 * Owns the transaction boundary.
 *
 * **Nothing is written when nothing changed.** The aggregate's `markActive` is an
 * idempotent no-op on an already-active booking, and this driver reads that back from the
 * event list rather than re-deriving the state: no events means no transition happened, so
 * there is nothing to persist and nothing to publish. Asking the aggregate what it did is
 * more honest than the driver guessing, and it keeps the idempotency rule in one place.
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md`.
 */
@Service
@Transactional
class MarkBookingActiveDriver(
    private val tourBookingRepository: TourBookingRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val clockPort: ClockPort,
) : MarkBookingActiveUseCase {
    override fun markActive(command: MarkBookingActiveCommand): MarkBookingActiveResult {
        val bookingId = BookingId.of(UUID.fromString(command.bookingId))
        val startedAt = command.startedAt ?: clockPort.now()

        val booking =
            tourBookingRepository.findById(bookingId)
                ?: throw BookingNotFoundException(command.bookingId)

        booking.markActive(startedAt, command.guideTourId)

        val events = booking.pullDomainEvents()
        if (events.isNotEmpty()) {
            tourBookingRepository.update(booking)
            events.forEach(domainEventPublisher::publish)
        }

        return MarkBookingActiveResult(booking.status.name)
    }
}
