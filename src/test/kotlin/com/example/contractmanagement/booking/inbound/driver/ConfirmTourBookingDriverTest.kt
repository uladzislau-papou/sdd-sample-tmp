package com.example.contractmanagement.booking.inbound.driver

import com.example.contractmanagement.booking.core.domain.tourbooking.AvailableCapacity
import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantContact
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantCount
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBooking
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBookingStatus
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.booking.core.domain.tourbooking.event.TourBookingConfirmed
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import com.example.contractmanagement.booking.core.inport.command.ConfirmTourBookingCommand
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.shared.outport.ClockPort
import com.example.contractmanagement.shared.outport.DomainEventPublisher
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
 * Use case tests for [ConfirmTourBookingDriver] (UC02).
 *
 * SDD: see `documentation/use-cases/uc02-confirm-tour-booking.spec.md`.
 */
class ConfirmTourBookingDriverTest {
    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")
    private val uuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private val repository: TourBookingRepository = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val clockPort: ClockPort = mock { on { now() } doReturn now }

    private val driver = ConfirmTourBookingDriver(repository, eventPublisher, clockPort)
    private val command = ConfirmTourBookingCommand(uuid.toString())

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
    fun confirm_returnsConfirmedStatus() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.REQUESTED))

        assertThat(driver.confirm(command).status).isEqualTo(TourBookingStatus.CONFIRMED.name)
    }

    @Test
    fun confirm_updatesTheAggregate() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.REQUESTED))

        driver.confirm(command)

        verify(repository).update(check { assertThat(it.status).isEqualTo(TourBookingStatus.CONFIRMED) })
    }

    @Test
    fun confirm_usesClockPort_forTheEventTimestamp() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.REQUESTED))

        driver.confirm(command)

        verify(eventPublisher).publish(
            check<TourBookingConfirmed> { assertThat(it.occurredAt).isEqualTo(now) },
        )
    }

    @Test
    fun confirm_throwsBookingNotFoundException_whenNoBookingHasThatIdentity() {
        whenever(repository.findById(any())).thenReturn(null)

        assertThatThrownBy { driver.confirm(command) }
            .isInstanceOf(BookingNotFoundException::class.java)
    }

    @Test
    fun confirm_throwsIllegalArgumentException_whenBookingIdIsMalformed() {
        // The source of the IllegalArgumentException -> 400 backstop: parsing an identifier
        // out of a path variable. Asserted here as well as at the boundary, because the
        // exception has to be thrown before it can be mapped, and it once surfaced as a 500.
        assertThatThrownBy { driver.confirm(ConfirmTourBookingCommand("not-a-uuid")) }
            .isInstanceOf(IllegalArgumentException::class.java)

        verify(repository, never()).update(any())
    }

    @Test
    fun confirm_propagatesInvalidBookingStateException_andUpdatesNothing() {
        whenever(repository.findById(any())).thenReturn(bookingIn(TourBookingStatus.ACTIVE))

        assertThatThrownBy { driver.confirm(command) }
            .isInstanceOf(InvalidBookingStateException::class.java)

        verify(repository, never()).update(any())
        verify(eventPublisher, never()).publish(any())
    }
}
