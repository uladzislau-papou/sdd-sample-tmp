package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancelledBy;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.CancelTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.CancelTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.CancelTourBookingUseCase;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC03 – CancelTourBooking use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, state transition,
 * persistence, and post-commit event publication without containing any domain rules.
 *
 * <p>UC08 extended it with attribution: the cancellation is always recorded as
 * {@link CancelledBy#USER}, because this driver sits behind the participant-facing REST
 * endpoint. A guide-initiated cancellation arrives through UC09's separate inport, so this
 * driver never needs to decide who is cancelling.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancel-tour-booking.spec.md} and
 * {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}.
 */
@Service
@Transactional
public class CancelTourBookingDriver implements CancelTourBookingUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public CancelTourBookingDriver(
            final TourBookingRepository tourBookingRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.tourBookingRepository = tourBookingRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public CancelTourBookingResult cancel(final CancelTourBookingCommand command) {
        final BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));

        // Built before the aggregate is loaded, so an invalid reason is a 400 regardless of
        // whether the booking exists and costs no database round trip (UC08 section 5).
        final CancellationReason reason = command.reason() == null
                ? null
                : new CancellationReason(command.reason());

        final Instant cancelledAt =
                Optional.ofNullable(command.cancelledAt()).orElseGet(clockPort::now);

        final TourBooking booking = tourBookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        booking.cancel(cancelledAt, CancelledBy.USER, reason);

        tourBookingRepository.update(booking);

        booking.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new CancelTourBookingResult(booking.status().name());
    }
}
