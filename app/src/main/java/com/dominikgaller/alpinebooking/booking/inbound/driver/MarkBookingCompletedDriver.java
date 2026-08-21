package com.dominikgaller.alpinebooking.booking.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.MarkBookingCompletedCommand;
import com.dominikgaller.alpinebooking.booking.core.inport.result.MarkBookingCompletedResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCompletedUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.shared.outport.ClockPort;
import com.dominikgaller.alpinebooking.shared.outport.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service (driver) implementing the UC07 – MarkBookingCompleted use case.
 *
 * <p>Mirrors {@code MarkBookingActiveDriver}: it resolves the timestamp, loads the
 * aggregate, delegates the transition, persists only when the aggregate actually changed,
 * and drains the events. The idempotency decision belongs to
 * {@code TourBooking.markCompleted} — this driver only observes whether an event resulted.
 *
 * <p>SDD: See {@code documentation/use-cases/uc07-mark-booking-completed.spec.md}.
 */
@Service
@Transactional
public class MarkBookingCompletedDriver implements MarkBookingCompletedUseCase {

    private final TourBookingRepository tourBookingRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final ClockPort clockPort;

    public MarkBookingCompletedDriver(
            final TourBookingRepository tourBookingRepository,
            final DomainEventPublisher domainEventPublisher,
            final ClockPort clockPort) {
        this.tourBookingRepository = tourBookingRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clockPort = clockPort;
    }

    @Override
    public MarkBookingCompletedResult markCompleted(final MarkBookingCompletedCommand command) {
        final Instant effectiveCompletion =
                Optional.ofNullable(command.completedAt()).orElseGet(clockPort::now);
        final BookingId bookingId = new BookingId(UUID.fromString(command.bookingId()));

        final TourBooking booking = tourBookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));

        booking.markCompleted(effectiveCompletion, command.guideTourId());

        final var events = booking.pullDomainEvents();
        if (!events.isEmpty()) {
            tourBookingRepository.update(booking);
            events.forEach(domainEventPublisher::publish);
        }

        return new MarkBookingCompletedResult(booking.status().name());
    }
}
