package com.dominikgaller.alpinebooking.booking.core.domain;

import com.dominikgaller.alpinebooking.booking.core.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingCancelled;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingConfirmed;
import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate root representing a reservation for a guided alpine tour.
 *
 * <p>State changes are performed via named methods ({@link #request}, {@link #confirm},
 * {@link #cancel}). Each method records the resulting domain event internally and exposes
 * events via {@link #pullDomainEvents()}.
 *
 * <p>Reconstitution from persistence uses {@link #reconstitute} — no invariants are
 * re-checked when loading an already-valid past fact.
 *
 * <p>Framework-free: no Spring, no JPA, no IO.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}.
 */
public class TourBooking {

    private final BookingId bookingId;
    private final TourId tourId;
    private final TourDate tourDate;
    private final ParticipantCount participantCount;
    private final AvailableCapacity availableCapacity;
    private final ParticipantContact contact;
    private TourBookingStatus status;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private TourBooking(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final TourBookingStatus status) {
        this.bookingId = bookingId;
        this.tourId = tourId;
        this.tourDate = tourDate;
        this.participantCount = participantCount;
        this.availableCapacity = availableCapacity;
        this.contact = contact;
        this.status = status;
    }

    /**
     * Creates a new booking in {@code REQUESTED} state.
     *
     * @param bookingId         unique identity; must not be null
     * @param tourId            tour reference; must not be null
     * @param tourDate          scheduled date; must be in the future relative to {@code now}
     * @param participantCount  number of participants; must be >= 1
     * @param availableCapacity open spots at booking time; must be >= participantCount
     * @param contact           contact person for the booking; must not be null
     * @param now               current time used to validate the tour date
     * @return a valid {@link TourBooking} in {@code REQUESTED} state
     * @throws InvalidBookingRequestException if {@code tourDate} is not in the future
     * @throws CapacityExceededException      if {@code participantCount} exceeds {@code availableCapacity}
     */
    public static TourBooking request(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final Instant now) {

        if (!tourDate.isInFuture(now)) {
            throw new InvalidBookingRequestException(
                    "Tour date must be in the future, was: " + tourDate.value());
        }
        if (participantCount.value() > availableCapacity.value()) {
            throw new CapacityExceededException(
                    participantCount.value(), availableCapacity.value());
        }

        final TourBooking booking = new TourBooking(
                bookingId, tourId, tourDate, participantCount,
                availableCapacity, contact, TourBookingStatus.REQUESTED);

        booking.domainEvents.add(new TourBookingRequested(
                bookingId, tourId, tourDate, participantCount, now));

        return booking;
    }

    /**
     * Reconstitutes a {@link TourBooking} from its persisted state.
     *
     * <p>No creation-time invariants are enforced — the data is assumed to have been
     * valid when first written. Called exclusively by the persistence mapper.
     *
     * @return a {@link TourBooking} reflecting the stored state, with no pending events
     */
    public static TourBooking reconstitute(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final TourBookingStatus status) {
        return new TourBooking(
                bookingId, tourId, tourDate, participantCount,
                availableCapacity, contact, status);
    }

    /**
     * Transitions the booking from {@code REQUESTED} to {@code CONFIRMED}.
     *
     * @throws InvalidBookingStateException if the current state is not {@code REQUESTED}
     */
    public void confirm(final Instant now) {
        if (status != TourBookingStatus.REQUESTED) {
            throw new InvalidBookingStateException(status);
        }
        status = TourBookingStatus.CONFIRMED;
        domainEvents.add(new TourBookingConfirmed(bookingId, now));
    }

    /**
     * Transitions the booking from {@code REQUESTED} or {@code CONFIRMED} to {@code CANCELLED}.
     *
     * @throws InvalidBookingStateException if the current state is neither {@code REQUESTED}
     *                                      nor {@code CONFIRMED}
     */
    public void cancel(final Instant now) {
        if (status != TourBookingStatus.REQUESTED && status != TourBookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(status);
        }
        status = TourBookingStatus.CANCELLED;
        domainEvents.add(new TourBookingCancelled(bookingId, now));
    }

    /**
     * Returns and clears all recorded domain events.
     *
     * <p>Calling this method twice returns an empty list on the second call.
     *
     * @return unmodifiable snapshot of pending events
     */
    public List<DomainEvent> pullDomainEvents() {
        final List<DomainEvent> snapshot = Collections.unmodifiableList(
                new ArrayList<>(domainEvents));
        domainEvents.clear();
        return snapshot;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public TourId tourId() {
        return tourId;
    }

    public TourDate tourDate() {
        return tourDate;
    }

    public ParticipantCount participantCount() {
        return participantCount;
    }

    public AvailableCapacity availableCapacity() {
        return availableCapacity;
    }

    public ParticipantContact contact() {
        return contact;
    }

    public TourBookingStatus status() {
        return status;
    }
}
