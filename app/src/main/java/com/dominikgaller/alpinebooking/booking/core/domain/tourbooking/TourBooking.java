package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingActivated;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCompleted;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.ParticipantsChanged;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCancelledByGuide;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCancelledByUser;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.TourBookingConfirmed;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Aggregate root representing a reservation for a guided alpine tour.
 *
 * <p>State changes are performed via named methods ({@link #request}, {@link #confirm},
 * {@link #cancel}, {@link #changeParticipants}, {@link #markActive}, {@link #markCompleted}).
 * Each records the resulting domain event internally and exposes events via
 * {@link #pullDomainEvents()}.
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
    private ParticipantCount participantCount;
    private AvailableCapacity availableCapacity;
    private final ParticipantContact contact;
    private TourBookingStatus status;

    private Instant cancelledAt;
    private CancelledBy cancelledBy;
    private CancellationReason cancellationReason;

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
     * @param cancelledAt        may be {@code null} if the booking was never cancelled
     * @param cancelledBy         may be {@code null} if the booking was never cancelled
     * @param cancellationReason  may be {@code null} — both for a live booking and for one
     *                            cancelled without giving a reason
     * @return a {@link TourBooking} reflecting the stored state, with no pending events
     */
    public static TourBooking reconstitute(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final TourBookingStatus status,
            final Instant cancelledAt,
            final CancelledBy cancelledBy,
            final CancellationReason cancellationReason) {
        return rehydrate(
                bookingId, tourId, tourDate, participantCount, availableCapacity,
                contact, status, cancelledAt, cancelledBy, cancellationReason);
    }

    /**
     * Convenience overload for a booking that was never cancelled.
     *
     * <p>Exists for the tests, which build aggregates in non-cancelled states constantly and
     * would otherwise repeat three trailing nulls.
     *
     * <p>Nothing structurally stops the mapper reaching for this overload and silently
     * dropping the attribution — {@code ClassRoleRulesTest} permits
     * {@code ..outbound.persistence..} to call either form, so "production uses the
     * full-arity method" is convention, not enforcement. What actually catches it is
     * {@code TourBookingJooqRepositoryIT.update_persistsCancellationAttribution} and
     * {@code .findById_returnsEmptyCancellationFields_forALiveBooking}: the first fails if
     * the attribution is lost on the way back, the second if it is invented.
     */
    public static TourBooking reconstitute(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final TourBookingStatus status) {
        return rehydrate(
                bookingId, tourId, tourDate, participantCount,
                availableCapacity, contact, status, null, null, null);
    }

    /**
     * Shared body for both {@code reconstitute} overloads.
     *
     * <p>Exists so the convenience overload does not <em>call</em> {@code reconstitute}:
     * {@code ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers} forbids any
     * class outside {@code ..outbound.persistence..} from doing so, and the aggregate is
     * outside it. The rule caught the self-call, and delegating through a private helper
     * satisfies it without narrowing the rule to carve out an exception nobody would
     * remember was an exception.
     */
    private static TourBooking rehydrate(
            final BookingId bookingId,
            final TourId tourId,
            final TourDate tourDate,
            final ParticipantCount participantCount,
            final AvailableCapacity availableCapacity,
            final ParticipantContact contact,
            final TourBookingStatus status,
            final Instant cancelledAt,
            final CancelledBy cancelledBy,
            final CancellationReason cancellationReason) {
        final TourBooking booking = new TourBooking(
                bookingId, tourId, tourDate, participantCount,
                availableCapacity, contact, status);
        booking.cancelledAt = cancelledAt;
        booking.cancelledBy = cancelledBy;
        booking.cancellationReason = cancellationReason;
        return booking;
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
     * Transitions the booking from {@code REQUESTED} or {@code CONFIRMED} to
     * {@code CANCELLED}, recording who cancelled and optionally why (UC08, UC09).
     *
     * <p>The emitted event is chosen by {@code cancelledBy}: {@link BookingCancelledByUser}
     * or {@link BookingCancelledByGuide}. Attribution lives in the event type rather than a
     * payload field so consumers subscribe rather than filter.
     *
     * <p>Not idempotent, unlike {@link #markActive} and {@link #markCompleted}: a second
     * cancellation is rejected rather than absorbed. Those two are driven by redeliverable
     * integration events, where a repeat is expected; this one is a deliberate act, and
     * silently accepting a second attempt would let a later caller overwrite the original
     * attribution — which is the one thing UC08 exists to record (AC-06).
     *
     * @param cancelledAt the moment of cancellation; must not be null
     * @param cancelledBy who initiated it; must not be null
     * @param reason      optional free text, already validated by
     *                    {@link CancellationReason}; may be null
     * @throws InvalidBookingStateException if the current state is neither {@code REQUESTED}
     *                                      nor {@code CONFIRMED}
     */
    public void cancel(
            final Instant cancelledAt,
            final CancelledBy cancelledBy,
            final CancellationReason reason) {
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        if (status != TourBookingStatus.REQUESTED && status != TourBookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(status);
        }
        status = TourBookingStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
        domainEvents.add(switch (cancelledBy) {
            case USER -> new BookingCancelledByUser(bookingId, cancelledAt, reason);
            case GUIDE -> new BookingCancelledByGuide(bookingId, cancelledAt, reason);
        });
    }

    /**
     * Transitions the booking from {@code CONFIRMED} to {@code ACTIVE}.
     *
     * <p>If the booking is already {@code ACTIVE} this method is a no-op (idempotent) and
     * no domain event is emitted.
     *
     * @param startedAt   the moment the tour execution started; must not be null
     * @param guideTourId optional correlation id linking to the guide tour execution; may be null.
     *                    Deliberately a plain {@link String}: the identity is owned by the
     *                    {@code guide} context, so {@code booking} treats it as opaque and never
     *                    parses or branches on it. See
     *                    {@code documentation/adr/0005-bounded-context-identity-boundaries.adr.md}.
     * @throws InvalidBookingStateException if the current state is neither {@code CONFIRMED}
     *                                      nor {@code ACTIVE}
     */
    public void markActive(final Instant startedAt, final String guideTourId) {
        if (status == TourBookingStatus.ACTIVE) {
            return;
        }
        if (status != TourBookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(status);
        }
        status = TourBookingStatus.ACTIVE;
        domainEvents.add(new BookingActivated(bookingId, startedAt, guideTourId));
    }

    /**
     * Transitions the booking from {@code ACTIVE} to {@code COMPLETED} (UC07).
     *
     * <p>If the booking is already {@code COMPLETED} this method is a no-op (idempotent) and
     * no domain event is emitted — the same shape as {@link #markActive}, because the same
     * {@code AFTER_COMMIT} listener may see a redelivered event.
     *
     * <p>{@code CONFIRMED → COMPLETED} is deliberately rejected: tolerating it would paper
     * over a missing {@code TourStarted} and let a booking complete a tour it never started.
     *
     * @param completedAt the moment the tour finished; carried on the event, not stored —
     *                    no invariant needs it, and neither does {@code markActive} store
     *                    {@code startedAt}
     * @param guideTourId optional correlation id linking to the guide tour execution; may be
     *                    null. Same contract and same rationale as {@link #markActive}'s
     *                    parameter of the same name — an opaque identity owned by the
     *                    {@code guide} context (ADR-0005)
     * @throws InvalidBookingStateException if the current state is neither {@code ACTIVE}
     *                                      nor {@code COMPLETED}
     */
    public void markCompleted(final Instant completedAt, final String guideTourId) {
        if (status == TourBookingStatus.COMPLETED) {
            return;
        }
        if (status != TourBookingStatus.ACTIVE) {
            throw new InvalidBookingStateException(status);
        }
        status = TourBookingStatus.COMPLETED;
        domainEvents.add(new BookingCompleted(bookingId, completedAt, guideTourId));
    }

    /**
     * Updates the participant count while respecting the current available capacity.
     *
     * <p>The caller must supply the freshly checked available capacity from the
     * external availability system. The aggregate stores the updated capacity snapshot.
     *
     * @param newCount      the desired participant count; must be >= 1
     * @param freshCapacity available capacity as of this request; must be >= {@code newCount}
     * @param now           current time used to timestamp the domain event
     * @throws InvalidBookingStateException if the current state is neither {@code REQUESTED}
     *                                      nor {@code CONFIRMED}
     * @throws CapacityExceededException    if {@code newCount} exceeds {@code freshCapacity}
     */
    public void changeParticipants(
            final ParticipantCount newCount,
            final AvailableCapacity freshCapacity,
            final Instant now) {

        if (status != TourBookingStatus.REQUESTED && status != TourBookingStatus.CONFIRMED) {
            throw new InvalidBookingStateException(status);
        }
        if (newCount.value() > freshCapacity.value()) {
            throw new CapacityExceededException(newCount.value(), freshCapacity.value());
        }
        this.participantCount = newCount;
        this.availableCapacity = freshCapacity;
        domainEvents.add(new ParticipantsChanged(bookingId, newCount, now));
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

    /**
     * The moment this booking was cancelled, empty unless it was (UC08).
     *
     * <p>{@link Optional} rather than a nullable {@link Instant}:
     * {@code coding-style.definition.md} section 1.4 forbids the domain returning null.
     */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    /** Who cancelled this booking, empty unless it was cancelled (UC08). */
    public Optional<CancelledBy> cancelledBy() {
        return Optional.ofNullable(cancelledBy);
    }

    /**
     * Why this booking was cancelled. Empty both when the booking is live and when it was
     * cancelled without a reason — giving one is optional (UC08 section 2).
     */
    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }
}
