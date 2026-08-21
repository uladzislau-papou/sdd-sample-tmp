package com.dominikgaller.alpinebooking.guide.core.domain.guidetour;

import com.dominikgaller.alpinebooking.shared.domain.event.TourStarted;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCompleted;
import com.dominikgaller.alpinebooking.shared.domain.event.TourCancelledByGuide;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Objects;
import java.util.List;

/**
 * Aggregate root representing a scheduled guide tour execution.
 *
 * <p>A {@code GuideTour} is the guide-side record of a tour session. It is independent
 * of individual {@code TourBooking} aggregates, which represent customer-side reservations.
 *
 * <p>State changes are performed via named methods ({@link #start}, {@link #complete}).
 * Each records the resulting domain event internally and exposes events via
 * {@link #pullDomainEvents()}.
 *
 * <p>Reconstitution from persistence uses {@link #reconstitute} — no invariants are
 * re-checked when loading an already-valid past fact. The consequence is that a
 * {@code RUNNING} aggregate with a null {@code startedAt} is representable, so
 * {@link #complete} guards for it explicitly (invariant I-06).
 *
 * <p>Framework-free: no Spring, no JPA, no IO.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-guide-tour.spec.md},
 * {@code documentation/use-cases/uc05-start-tour.spec.md} and
 * {@code documentation/use-cases/uc11-complete-tour.spec.md}.
 */
public class GuideTour {

    private final GuideTourId id;
    private final TourId tourId;
    private final Instant scheduledStart;
    private GuideTourStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;
    private CancellationReason cancellationReason;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private GuideTour(
            final GuideTourId id,
            final TourId tourId,
            final Instant scheduledStart,
            final GuideTourStatus status,
            final Instant startedAt,
            final Instant completedAt) {
        this.id = id;
        this.tourId = tourId;
        this.scheduledStart = scheduledStart;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /**
     * Creates a new guide tour in {@code SCHEDULED} state.
     *
     * @param id             unique identity; must not be null
     * @param tourId         tour reference; must not be null
     * @param scheduledStart planned start time; must not be null
     * @return a valid {@link GuideTour} in {@code SCHEDULED} state with no pending events
     */
    public static GuideTour schedule(
            final GuideTourId id,
            final TourId tourId,
            final Instant scheduledStart) {
        Objects.requireNonNull(id, "GuideTourId must not be null");
        Objects.requireNonNull(tourId, "TourId must not be null");
        Objects.requireNonNull(scheduledStart, "scheduledStart must not be null");
        return new GuideTour(id, tourId, scheduledStart, GuideTourStatus.SCHEDULED, null, null);
    }

    /**
     * Reconstitutes a {@link GuideTour} from its persisted state.
     *
     * <p>No creation-time invariants are enforced — the data is assumed to have been
     * valid when first written. Called exclusively by the persistence mapper.
     *
     * @param startedAt   may be {@code null} if the tour has not been started yet
     * @param completedAt may be {@code null} if the tour has not been completed yet
     * @return a {@link GuideTour} reflecting the stored state, with no pending events
     */
    public static GuideTour reconstitute(
            final GuideTourId id,
            final TourId tourId,
            final Instant scheduledStart,
            final GuideTourStatus status,
            final Instant startedAt,
            final Instant completedAt,
            final Instant cancelledAt,
            final CancellationReason cancellationReason) {
        final GuideTour tour = new GuideTour(
                id, tourId, scheduledStart, status, startedAt, completedAt);
        tour.cancelledAt = cancelledAt;
        tour.cancellationReason = cancellationReason;
        return tour;
    }

    /**
     * Convenience overload for a tour that was never cancelled.
     *
     * <p>Exists for the tests, which build non-cancelled aggregates constantly. Note this
     * does <b>not</b> call the full-arity {@code reconstitute}: doing so would breach
     * {@code ClassRoleRulesTest.reconstitute_isCalledOnlyByPersistenceMappers}, which forbids
     * any class outside {@code ..outbound.persistence..} — the aggregate included — from
     * calling it. The booking side hit the same rule and solved it the same way.
     *
     * <p>That production reconstitution uses the full-arity form is convention, not
     * enforcement; what catches a mapper dropping the cancellation fields is
     * {@code GuideTourJooqRepositoryIT.update_changesStatus_toCancelled} and
     * {@code .cancellationFields_areEmpty_forATourThatWasNeverCancelled}.
     */
    public static GuideTour reconstitute(
            final GuideTourId id,
            final TourId tourId,
            final Instant scheduledStart,
            final GuideTourStatus status,
            final Instant startedAt,
            final Instant completedAt) {
        final GuideTour tour = new GuideTour(
                id, tourId, scheduledStart, status, startedAt, completedAt);
        tour.cancelledAt = null;
        tour.cancellationReason = null;
        return tour;
    }

    /**
     * Transitions the guide tour from {@code SCHEDULED} to {@code RUNNING}.
     *
     * @param startedAt the actual start time; must not be null
     * @throws InvalidGuideTourStateException if the current state is not {@code SCHEDULED}
     * @throws TourStartTooEarlyException     if {@code startedAt} is before {@code scheduledStart}
     */
    public void start(final Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (status != GuideTourStatus.SCHEDULED) {
            throw new InvalidGuideTourStateException(status);
        }
        if (startedAt.isBefore(scheduledStart)) {
            throw new TourStartTooEarlyException(scheduledStart, startedAt);
        }
        this.status = GuideTourStatus.RUNNING;
        this.startedAt = startedAt;
        domainEvents.add(new TourStarted(id.value().toString(), tourId, startedAt));
    }

    /**
     * Transitions the guide tour from {@code RUNNING} to {@code FINISHED} (UC11).
     *
     * @param completedAt the actual completion time; must not be null
     * @throws InvalidGuideTourStateException     if the current state is not {@code RUNNING}
     * @throws TourCompletedBeforeStartException  if {@code completedAt} is before {@code startedAt}
     * @throws IllegalStateException              if the tour is {@code RUNNING} with no
     *                                            {@code startedAt} — corrupt data (I-06)
     */
    public void complete(final Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        if (status != GuideTourStatus.RUNNING) {
            throw new InvalidGuideTourStateException(status);
        }
        if (startedAt == null) {
            // RUNNING with no startedAt is an impossible state that only corrupt data or
            // misuse of reconstitute can produce - the column is nullable with no CHECK.
            // Guarded explicitly so it surfaces as a named data fault rather than as an
            // NPE from the comparison below. See I-06/I-07 in the aggregate spec.
            throw new IllegalStateException(
                    "GuideTour " + id.value() + " is RUNNING but has no startedAt; "
                            + "cannot determine whether completion precedes the start");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new TourCompletedBeforeStartException(startedAt, completedAt);
        }
        this.status = GuideTourStatus.FINISHED;
        this.completedAt = completedAt;
        domainEvents.add(new TourCompleted(id.value().toString(), tourId, completedAt));
    }

    /**
     * Transitions the guide tour to {@code CANCELLED} (UC12).
     *
     * <p>Permitted from {@code SCHEDULED} and from {@code RUNNING} — aborting a tour
     * mid-execution is the whole point, and it is the case UC09 AC-02 exists for on the
     * booking side. {@code FINISHED} is rejected: a completed tour cannot be retroactively
     * called off.
     *
     * <p>Unlike {@code TourBooking.cancel} for a guide, a second cancellation here
     * <b>throws</b> rather than being an idempotent no-op. This is the deliberate act at the
     * top of the chain, reached over REST by a person, so a 409 telling them the tour was
     * already called off is the useful answer. The booking-side no-op exists because that
     * side is the fan-out target and must not abort a batch.
     *
     * @param cancelledAt the moment of cancellation; must not be null
     * @param reason      optional; may be null. Already validated by
     *                    {@link CancellationReason}, so this method has no length rule of
     *                    its own — the type carries it
     * @throws InvalidGuideTourStateException if the current state is neither
     *                                        {@code SCHEDULED} nor {@code RUNNING}
     */
    public void cancel(final Instant cancelledAt, final CancellationReason reason) {
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
        if (status != GuideTourStatus.SCHEDULED && status != GuideTourStatus.RUNNING) {
            throw new InvalidGuideTourStateException(status);
        }
        this.status = GuideTourStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
        this.cancellationReason = reason;
        domainEvents.add(new TourCancelledByGuide(
                id.value().toString(), tourId, cancelledAt,
                reason == null ? null : reason.value()));
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

    public GuideTourId id() {
        return id;
    }

    public TourId tourId() {
        return tourId;
    }

    public Instant scheduledStart() {
        return scheduledStart;
    }

    public GuideTourStatus status() {
        return status;
    }

    /**
     * The moment the tour actually started, empty until it has.
     *
     * <p>Returns {@link Optional} rather than a nullable {@link Instant}:
     * {@code coding-style.definition.md} section 1.4 forbids the domain returning null.
     */
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt);
    }

    /**
     * The moment the tour was completed, empty until it has been (UC11).
     */
    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    /** The moment the tour was cancelled, empty unless it was (UC12). */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    /**
     * Why the tour was cancelled. Empty both for a live tour and for one cancelled without a
     * reason — giving one is optional (UC12 section 2).
     */
    public Optional<CancellationReason> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }
}
