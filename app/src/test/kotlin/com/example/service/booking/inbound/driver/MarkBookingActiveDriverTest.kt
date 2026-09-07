package com.example.service.booking.inbound.driver

import com.example.service.booking.core.domain.tourbooking.AvailableCapacity
import com.example.service.booking.core.domain.tourbooking.BookingId
import com.example.service.booking.core.domain.tourbooking.ParticipantContact
import com.example.service.booking.core.domain.tourbooking.ParticipantCount
import com.example.service.booking.core.domain.tourbooking.TourBooking
import com.example.service.booking.core.domain.tourbooking.TourBookingStatus
import com.example.service.booking.core.domain.tourbooking.TourDate
import com.example.service.booking.core.domain.tourbooking.event.BookingActivated
import com.example.service.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import com.example.service.booking.core.inport.command.MarkBookingActiveCommand
import com.example.service.booking.core.outport.TourBookingRepository
import com.example.service.shared.domain.TourId
import com.example.service.shared.outport.ClockPort
import com.example.service.shared.outport.DomainEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Use case tests for [MarkBookingActiveDriver] (UC06).
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md`.
 */
class MarkBookingActiveDriverTest {
    private val clockNow: Instant = Instant.parse("2026-06-01T10:00:00Z")
    private val relayedStartedAt: Instant = Instant.parse("2026-06-01T09:30:00Z")
    private val uuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private val repository: TourBookingRepository = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val clockPort: ClockPort = mock { on { now() } doReturn clockNow }

    private val driver = MarkBookingActiveDriver(repository, eventPublisher, clockPort)

    private fun command(startedAt: Instant? = relayedStartedAt) =
        MarkBookingActiveCommand(
            bookingId = uuid.toString(),
            startedAt = startedAt,
            guideTourId = "guide-tour-7",
        )

    private fun bookingIn(status: TourBookingStatus): TourBooking =
        TourBooking.reconstitute(
            bookingId = BookingId.of(uuid),
            tourId = TourId("tour-42"),
            tourDate = TourDate(LocalDate.parse("2026-07-01")),
            participantCount = ParticipantCount(2),
            availableCapacity = AvailableCapacity(10),
            contact = ParticipantContact("Ada Lovelace", "ada@example.com"),
            status = status,
        )

    @Test
    fun markActive_returnsActiveStatus() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.CONFIRMED))

        assertThat(driver.markActive(command()).status).isEqualTo(TourBookingStatus.ACTIVE.name)
    }

    @Test
    fun markActive_updatesTheAggregate() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.CONFIRMED))

        driver.markActive(command())

        verify(repository).update(check { assertThat(it.status).isEqualTo(TourBookingStatus.ACTIVE) })
    }

    @Test
    fun markActive_prefersTheCommandTimestamp_overTheClock() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.CONFIRMED))

        driver.markActive(command())

        verify(eventPublisher).publish(
            check<BookingActivated> { assertThat(it.activatedAt).isEqualTo(relayedStartedAt) },
        )
    }

    @Test
    fun markActive_usesClockPort_whenTheCommandCarriesNoTimestamp() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.CONFIRMED))

        driver.markActive(command(startedAt = null))

        verify(eventPublisher).publish(
            check<BookingActivated> { assertThat(it.activatedAt).isEqualTo(clockNow) },
        )
    }

    @Test
    fun markActive_isIdempotent_persistsAndPublishesNothing_whenAlreadyActive() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.ACTIVE))

        val result = driver.markActive(command())

        assertThat(result.status).isEqualTo(TourBookingStatus.ACTIVE.name)
        verify(repository, never()).update(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun markActive_propagatesInvalidBookingStateException_whenCancelled() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.CANCELLED))

        assertThatThrownBy { driver.markActive(command()) }
            .isInstanceOf(InvalidBookingStateException::class.java)

        verify(repository, never()).update(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun markActive_throwsBookingNotFoundException_whenNoBookingHasThatIdentity() {
        whenever(repository.findById(any())).thenReturn(null)

        assertThatThrownBy { driver.markActive(command()) }
            .isInstanceOf(BookingNotFoundException::class.java)
    }
}
