package com.example.contractmanagement.booking.core.domain.tourbooking

import com.example.contractmanagement.booking.core.domain.tourbooking.event.BookingActivated
import com.example.contractmanagement.booking.core.domain.tourbooking.event.TourBookingConfirmed
import com.example.contractmanagement.booking.core.domain.tourbooking.event.TourBookingRequested
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import com.example.contractmanagement.shared.domain.TourId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Domain tests for the [TourBooking] aggregate.
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
class TourBookingTest {
    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")
    private val futureDate = TourDate(LocalDate.parse("2026-07-01"))
    private val bookingId = BookingId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"))
    private val tourId = TourId("tour-42")
    private val contact = ParticipantContact("Ada Lovelace", "ada@example.com")

    private fun requested(): TourBooking =
        TourBooking.request(
            bookingId = bookingId,
            tourId = tourId,
            tourDate = futureDate,
            participantCount = ParticipantCount(2),
            availableCapacity = AvailableCapacity(10),
            contact = contact,
            now = now,
        )

    private fun inState(status: TourBookingStatus): TourBooking =
        TourBooking.reconstitute(
            bookingId = bookingId,
            tourId = tourId,
            tourDate = futureDate,
            participantCount = ParticipantCount(2),
            availableCapacity = AvailableCapacity(10),
            contact = contact,
            status = status,
        )

    @Test
    fun request_setsStatus_toRequested() {
        assertThat(requested().status).isEqualTo(TourBookingStatus.REQUESTED)
    }

    @Test
    fun request_storesAllFields_asGiven() {
        val booking = requested()

        assertThat(booking.bookingId).isEqualTo(bookingId)
        assertThat(booking.tourId).isEqualTo(tourId)
        assertThat(booking.tourDate).isEqualTo(futureDate)
        assertThat(booking.participantCount).isEqualTo(ParticipantCount(2))
        assertThat(booking.availableCapacity).isEqualTo(AvailableCapacity(10))
        assertThat(booking.contact).isEqualTo(contact)
    }

    @Test
    fun request_recordsExactlyOneEvent_tourBookingRequested() {
        val events = requested().pullDomainEvents()

        assertThat(events).hasSize(1)
        assertThat(events.first())
            .isInstanceOf(TourBookingRequested::class.java)
            .isEqualTo(TourBookingRequested(bookingId, tourId, futureDate, ParticipantCount(2), now))
    }

    @Test
    fun pullDomainEvents_returnsEmpty_onSecondCall() {
        val booking = requested()
        booking.pullDomainEvents()

        assertThat(booking.pullDomainEvents()).isEmpty()
    }

    @Test
    fun request_throwsInvalidBookingRequestException_whenTourDateIsNotInFuture() {
        assertThatThrownBy {
            TourBooking.request(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = TourDate(LocalDate.parse("2026-05-31")),
                participantCount = ParticipantCount(2),
                availableCapacity = AvailableCapacity(10),
                contact = contact,
                now = now,
            )
        }.isInstanceOf(InvalidBookingRequestException::class.java)
    }

    @Test
    fun request_throwsCapacityExceededException_whenCountExceedsCapacity() {
        assertThatThrownBy {
            TourBooking.request(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = futureDate,
                participantCount = ParticipantCount(5),
                availableCapacity = AvailableCapacity(4),
                contact = contact,
                now = now,
            )
        }.isInstanceOf(CapacityExceededException::class.java)
    }

    @Test
    fun request_succeeds_whenCountEqualsCapacity() {
        val booking =
            TourBooking.request(
                bookingId = bookingId,
                tourId = tourId,
                tourDate = futureDate,
                participantCount = ParticipantCount(4),
                availableCapacity = AvailableCapacity(4),
                contact = contact,
                now = now,
            )

        assertThat(booking.status).isEqualTo(TourBookingStatus.REQUESTED)
    }

    @Test
    fun confirm_transitionsStatus_toConfirmed() {
        val booking = inState(TourBookingStatus.REQUESTED)

        booking.confirm(now)

        assertThat(booking.status).isEqualTo(TourBookingStatus.CONFIRMED)
    }

    @Test
    fun confirm_recordsTourBookingConfirmedEvent() {
        val booking = inState(TourBookingStatus.REQUESTED)

        booking.confirm(now)

        assertThat(booking.pullDomainEvents())
            .containsExactly(TourBookingConfirmed(bookingId, now))
    }

    @Test
    fun confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed() {
        val booking = inState(TourBookingStatus.CONFIRMED)

        assertThatThrownBy { booking.confirm(now) }
            .isInstanceOf(InvalidBookingStateException::class.java)
    }

    @Test
    fun confirm_leavesStatusUnchanged_whenStateInvalid() {
        val booking = inState(TourBookingStatus.ACTIVE)

        runCatching { booking.confirm(now) }

        assertThat(booking.status).isEqualTo(TourBookingStatus.ACTIVE)
    }

    @Test
    fun markActive_transitionsStatus_toActive() {
        val booking = inState(TourBookingStatus.CONFIRMED)

        booking.markActive(now, "guide-tour-7")

        assertThat(booking.status).isEqualTo(TourBookingStatus.ACTIVE)
    }

    @Test
    fun markActive_recordsBookingActivatedEvent_carryingTheGuideTourId() {
        val booking = inState(TourBookingStatus.CONFIRMED)

        booking.markActive(now, "guide-tour-7")

        assertThat(booking.pullDomainEvents())
            .containsExactly(BookingActivated(bookingId, now, "guide-tour-7"))
    }

    @Test
    fun markActive_acceptsNullGuideTourId() {
        val booking = inState(TourBookingStatus.CONFIRMED)

        booking.markActive(now, null)

        assertThat(booking.pullDomainEvents())
            .containsExactly(BookingActivated(bookingId, now, null))
    }

    @Test
    fun markActive_isIdempotentNoOp_whenAlreadyActive() {
        val booking = inState(TourBookingStatus.ACTIVE)

        booking.markActive(now, "guide-tour-7")

        assertThat(booking.status).isEqualTo(TourBookingStatus.ACTIVE)
        assertThat(booking.pullDomainEvents()).isEmpty()
    }

    @Test
    fun markActive_throwsInvalidBookingStateException_whenStillRequested() {
        val booking = inState(TourBookingStatus.REQUESTED)

        assertThatThrownBy { booking.markActive(now, null) }
            .isInstanceOf(InvalidBookingStateException::class.java)
    }

    @Test
    fun markActive_throwsInvalidBookingStateException_whenCancelled() {
        val booking = inState(TourBookingStatus.CANCELLED)

        assertThatThrownBy { booking.markActive(now, null) }
            .isInstanceOf(InvalidBookingStateException::class.java)
    }

    @Test
    fun markActive_throwsInvalidBookingStateException_whenCompleted() {
        val booking = inState(TourBookingStatus.COMPLETED)

        assertThatThrownBy { booking.markActive(now, null) }
            .isInstanceOf(InvalidBookingStateException::class.java)
    }

    @Test
    fun reconstitute_recordsNoEvents() {
        assertThat(inState(TourBookingStatus.CONFIRMED).pullDomainEvents()).isEmpty()
    }
}
