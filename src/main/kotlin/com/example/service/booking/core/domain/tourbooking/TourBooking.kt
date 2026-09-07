package com.example.service.booking.core.domain.tourbooking

import com.example.service.booking.core.domain.tourbooking.event.BookingActivated
import com.example.service.booking.core.domain.tourbooking.event.TourBookingConfirmed
import com.example.service.booking.core.domain.tourbooking.event.TourBookingRequested
import com.example.service.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import com.example.service.shared.domain.TourId
import com.example.service.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * Aggregate root representing a reservation for a guided tour.
 *
 * State changes happen through named transition methods — [confirm], [markActive] — each
 * of which enforces the invariants of that transition and records the resulting domain
 * event. Events are handed off through [pullDomainEvents].
 *
 * Deliberately **not** a `data class`: structural equality is wrong for an entity, and a
 * generated `copy()` would hand every caller a way around these transition methods
 * (`coding-style.definition.md` § 2.1). Mutable state uses `private set` for the same
 * reason (§ 5.1).
 *
 * Reconstitution from persistence goes through [reconstitute], which re-checks no
 * invariants: the row was valid when it was written, and re-validating a past fact means
 * a schema change can make history unreadable.
 *
 * Framework-free: no Spring, no JPA, no IO.
 *
 * `LongParameterList` is suppressed for the whole class. An aggregate assembles its
 * entire state in one constructor and its factories mirror that, so the count is a
 * property of the state, not of a behaviour with too many knobs — which is what the
 * rule is for, and which its default threshold still catches everywhere else. The two
 * ways to satisfy it here would be a parameter object nothing else uses, or setters,
 * and setters on an aggregate are forbidden outright (`coding-style.definition.md`
 * § 5.1).
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
@Suppress("LongParameterList")
class TourBooking private constructor(
    val bookingId: BookingId,
    val tourId: TourId,
    val tourDate: TourDate,
    participantCount: ParticipantCount,
    availableCapacity: AvailableCapacity,
    val contact: ParticipantContact,
    status: TourBookingStatus,
) {
    var participantCount: ParticipantCount = participantCount
        private set

    var availableCapacity: AvailableCapacity = availableCapacity
        private set

    var status: TourBookingStatus = status
        private set

    private val domainEvents = mutableListOf<DomainEvent>()

    /**
     * Transitions a requested booking to `CONFIRMED` (UC02).
     *
     * @param now the moment of confirmation, supplied by the driver from `ClockPort`
     * @throws InvalidBookingStateException if the booking is not `REQUESTED`
     */
    fun confirm(now: Instant) {
        if (status != TourBookingStatus.REQUESTED) {
            throw InvalidBookingStateException(status)
        }
        status = TourBookingStatus.CONFIRMED
        domainEvents += TourBookingConfirmed(bookingId, now)
    }

    /**
     * Transitions a confirmed booking to `ACTIVE` because its tour started (UC06).
     *
     * Idempotent: activating an already-active booking is a no-op that records no event.
     * That is not politeness, it is required — the caller is an `AFTER_COMMIT` listener,
     * and a redelivered event must not fail the second time.
     *
     * `REQUESTED -> ACTIVE` is deliberately rejected. Tolerating it would let a booking
     * nobody confirmed be carried along by a tour starting.
     *
     * @param startedAt the moment tour execution began
     * @param guideTourId opaque correlation id owned by the `guide` context; may be null
     * @throws InvalidBookingStateException if the booking is neither `CONFIRMED` nor
     *   already `ACTIVE`
     */
    fun markActive(
        startedAt: Instant,
        guideTourId: String?,
    ) {
        if (status == TourBookingStatus.ACTIVE) {
            return
        }
        if (status != TourBookingStatus.CONFIRMED) {
            throw InvalidBookingStateException(status)
        }
        status = TourBookingStatus.ACTIVE
        domainEvents += BookingActivated(bookingId, startedAt, guideTourId)
    }

    /**
     * Returns and clears the recorded domain events.
     *
     * A second call returns an empty list. The snapshot is a copy, so a caller iterating
     * it cannot be surprised by a later transition.
     */
    fun pullDomainEvents(): List<DomainEvent> {
        val snapshot = domainEvents.toList()
        domainEvents.clear()
        return snapshot
    }

    companion object {
        /**
         * Creates a booking in `REQUESTED` state (UC01).
         *
         * @throws InvalidBookingRequestException if [tourDate] is not in the future
         * @throws CapacityExceededException if [participantCount] exceeds
         *   [availableCapacity]
         */
        fun request(
            bookingId: BookingId,
            tourId: TourId,
            tourDate: TourDate,
            participantCount: ParticipantCount,
            availableCapacity: AvailableCapacity,
            contact: ParticipantContact,
            now: Instant,
        ): TourBooking {
            if (!tourDate.isInFuture(now)) {
                throw InvalidBookingRequestException(
                    "Tour date must be in the future, was: ${tourDate.value}",
                )
            }
            if (participantCount.value > availableCapacity.value) {
                throw CapacityExceededException(participantCount.value, availableCapacity.value)
            }

            return TourBooking(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = tourDate,
                participantCount = participantCount,
                availableCapacity = availableCapacity,
                contact = contact,
                status = TourBookingStatus.REQUESTED,
            ).also {
                it.domainEvents += TourBookingRequested(bookingId, tourId, tourDate, participantCount, now)
            }
        }

        /**
         * Rebuilds a booking from its persisted state, with no pending events.
         *
         * Called by the persistence mapper. `ClassRoleRulesTest` enforces that: a caller
         * outside `..outbound.persistence..` reaching for this method is skipping the
         * invariants that [request] enforces.
         */
        fun reconstitute(
            bookingId: BookingId,
            tourId: TourId,
            tourDate: TourDate,
            participantCount: ParticipantCount,
            availableCapacity: AvailableCapacity,
            contact: ParticipantContact,
            status: TourBookingStatus,
        ): TourBooking =
            TourBooking(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = tourDate,
                participantCount = participantCount,
                availableCapacity = availableCapacity,
                contact = contact,
                status = status,
            )
    }
}
