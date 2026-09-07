package com.example.service.booking.inbound.listener

import com.example.service.booking.core.inport.command.MarkBookingActiveCommand
import com.example.service.booking.core.inport.usecase.MarkBookingActiveUseCase
import com.example.service.booking.core.outport.TourBookingRepository
import com.example.service.shared.domain.event.TourStarted
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Reacts to `TourStarted` from the `guide` context and activates every confirmed booking
 * for that tour (UC06).
 *
 * **An inbound adapter with no API.** It is driven by an event rather than a request,
 * which is why UC06 has no file in `api/` and its spec marks § 9 not applicable. It is
 * also the *only* trigger for `CONFIRMED -> ACTIVE`: there is deliberately no endpoint for
 * direct activation, because a booking becoming active is a consequence of a tour starting
 * and not an independent decision anyone should be able to make.
 *
 * Runs in a **new** transaction after the guide tour's transaction commits (ADR 0002), so
 * the activation is durable regardless of what the originating context does next — and so
 * a failure here cannot roll back a tour that really did start.
 *
 * The fan-out calls the use case once per booking rather than mutating them in a loop
 * here. That keeps the idempotency rule and the transaction boundary in the driver, where
 * a redelivered event is already handled.
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md`.
 */
@Component
class TourStartedListener(
    private val tourBookingRepository: TourBookingRepository,
    private val markBookingActiveUseCase: MarkBookingActiveUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onTourStarted(event: TourStarted) {
        tourBookingRepository.findConfirmedByTourId(event.tourId).forEach { booking ->
            markBookingActiveUseCase.markActive(
                MarkBookingActiveCommand(
                    bookingId = booking.bookingId.value.toString(),
                    startedAt = event.startedAt,
                    guideTourId = event.guideTourId,
                ),
            )
        }
    }
}
