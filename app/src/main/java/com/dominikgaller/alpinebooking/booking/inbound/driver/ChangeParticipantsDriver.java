package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.ChangeParticipantsCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ChangeParticipantsResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.ChangeParticipantsUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityChecker;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC04 – ChangeParticipants use case.
 *
 * <p>Owns the transaction boundary. Orchestrates aggregate loading, availability check,
 * state mutation, persistence, and post-commit event publication without containing
 * any domain rules.
 *
 * <p>SDD: See {@code documentation/use-cases/uc04-change-participants.spec.md}.
 */
@Service
@Transactional
public class ChangeParticipantsDriver implements ChangeParticipantsUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final AvailabilityChecker availabilityChecker;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public ChangeParticipantsDriver(
            final TourBookingRepository tourBookingRepository,
            final AvailabilityChecker availabilityChecker,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.tourBookingRepository = tourBookingRepository;
        this.availabilityChecker = availabilityChecker;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public ChangeParticipantsResult change(final ChangeParticipantsCommand command) {
        final BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));
        final Instant now = clockPort.now();

        final TourBooking booking = tourBookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        final AvailableCapacity freshCapacity =
                availabilityChecker.checkAvailability(booking.tourId(), booking.tourDate());

        booking.changeParticipants(new ParticipantCount(command.newParticipantCount()), freshCapacity, now);

        tourBookingRepository.update(booking);

        booking.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new ChangeParticipantsResult(booking.participantCount().value());
    }
}
