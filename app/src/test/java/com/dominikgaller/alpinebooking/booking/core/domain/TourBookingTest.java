package com.dominikgaller.alpinebooking.booking.core.domain;

import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TourBookingTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private static final BookingId BOOKING_ID = BookingId.generate();
    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final TourDate TOUR_DATE = new TourDate(FUTURE_DATE);
    private static final ParticipantCount COUNT_2 = new ParticipantCount(2);
    private static final AvailableCapacity CAPACITY_10 = new AvailableCapacity(10);
    private static final ParticipantContact CONTACT =
            new ParticipantContact("Alice", "alice@example.com");

    private TourBooking validBooking() {
        return TourBooking.request(BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT, NOW);
    }

    @Test
    void requestSetsStatusToRequested() {
        assertThat(validBooking().status()).isEqualTo(TourBookingStatus.REQUESTED);
    }

    @Test
    void requestStoresAllFieldsCorrectly() {
        final TourBooking booking = validBooking();
        assertThat(booking.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(booking.tourId()).isEqualTo(TOUR_ID);
        assertThat(booking.tourDate()).isEqualTo(TOUR_DATE);
        assertThat(booking.participantCount()).isEqualTo(COUNT_2);
        assertThat(booking.availableCapacity()).isEqualTo(CAPACITY_10);
        assertThat(booking.contact()).isEqualTo(CONTACT);
    }

    @Test
    void pullDomainEventsReturnsExactlyOneTourBookingRequested() {
        final TourBooking booking = validBooking();
        final List<TourBookingRequested> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        final TourBookingRequested event = events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.tourId()).isEqualTo(TOUR_ID);
        assertThat(event.tourDate()).isEqualTo(TOUR_DATE);
        assertThat(event.participantCount()).isEqualTo(COUNT_2);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void pullDomainEventsCalledTwiceReturnsEmptyListOnSecondCall() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents();
        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    @Test
    void pastTourDateThrowsInvalidBookingRequestException() {
        final TourDate pastDate = new TourDate(LocalDate.of(2025, 1, 1));
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> TourBooking.request(
                        BOOKING_ID, TOUR_ID, pastDate, COUNT_2, CAPACITY_10, CONTACT, NOW));
    }

    @Test
    void participantCountExceedingCapacityThrowsCapacityExceededException() {
        final ParticipantCount tooMany = new ParticipantCount(11);
        assertThatExceptionOfType(CapacityExceededException.class)
                .isThrownBy(() -> TourBooking.request(
                        BOOKING_ID, TOUR_ID, TOUR_DATE, tooMany, CAPACITY_10, CONTACT, NOW));
    }

    @Test
    void participantCountEqualToCapacityIsValid() {
        final ParticipantCount exactCount = new ParticipantCount(10);
        final TourBooking booking = TourBooking.request(
                BOOKING_ID, TOUR_ID, TOUR_DATE, exactCount, CAPACITY_10, CONTACT, NOW);
        assertThat(booking.status()).isEqualTo(TourBookingStatus.REQUESTED);
    }
}
