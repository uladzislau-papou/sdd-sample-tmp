package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingActiveCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingActiveResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingActiveUseCase;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC06 – MarkBookingActive use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, state transition,
 * persistence, and post-commit event publication without containing any domain rules.
 *
 * <p>If the booking is already ACTIVE the call is idempotent: no update is persisted
 * and no event is published.
 *
 * <p>SDD: See {@code documentation/use-cases/uc06-mark-booking-active.spec.md}.
 */
@Service
@Transactional
public class MarkBookingActiveDriver implements MarkBookingActiveUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public MarkBookingActiveDriver(
            final TourBookingRepository tourBookingRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.tourBookingRepository = tourBookingRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public MarkBookingActiveResult markActive(final MarkBookingActiveCommand command) {
        final BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        final Instant startedAt = command.startedAt() != null ? command.startedAt() : clockPort.now();

        final TourBooking booking = tourBookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        booking.markActive(startedAt, command.guideTourId());

        final var events = booking.pullDomainEvents();
        if (!events.isEmpty()) {
            tourBookingRepository.update(booking);
            events.forEach(domainEventPublisher::publish);
        }

        return new MarkBookingActiveResult(booking.status().name());
    }
}
