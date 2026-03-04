package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.booking.core.inport.command.RequestTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.RequestTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.RequestTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityChecker;
import com.dominikgaller.alpinebooking.booking.core.outport.ClockPort;
import com.dominikgaller.alpinebooking.booking.core.outport.DomainEventPublisher;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service (driver) implementing the UC01 – RequestTourBooking use case.
 *
 * <p>Owns the transaction boundary. Orchestrates domain creation, persistence,
 * and post-commit event publication without containing any domain rules.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}.
 */
@Service
@Transactional
public class RequestTourBookingDriver implements RequestTourBookingUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final AvailabilityChecker availabilityChecker;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public RequestTourBookingDriver(
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
    public RequestTourBookingResult request(final RequestTourBookingCommand command) {
        final TourId tourId = new TourId(command.tourId());
        final TourDate tourDate = new TourDate(command.tourDate());
        final ParticipantCount participantCount = new ParticipantCount(command.participantCount());
        final ParticipantContact contact = new ParticipantContact(command.contactName(), command.contactEmail());
        final BookingId bookingId = BookingId.generate();
        final Instant now = clockPort.now();

        final AvailableCapacity availableCapacity =
                availabilityChecker.checkAvailability(tourId, tourDate);

        final TourBooking booking = TourBooking.request(
                bookingId, tourId, tourDate, participantCount, availableCapacity, contact, now);

        tourBookingRepository.save(booking);

        booking.pullDomainEvents().forEach(domainEventPublisher::publish);

        return new RequestTourBookingResult(
                bookingId.value().toString(),
                booking.status().name());
    }
}
