package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
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
import java.util.UUID;

/**
 * Application service (driver) implementing the UC03 – CancelTourBooking use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, state transition,
 * persistence, and post-commit event publication without containing any domain rules.
 *
 * <p>SDD: See {@code documentation/use-cases/uc03-cancle-tour-booking.spec.md}.
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
        final Instant now = clockPort.now();

        final TourBooking booking = tourBookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        booking.cancel(now);

        tourBookingRepository.update(booking);

        booking.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new CancelTourBookingResult(booking.status().name());
    }
}
