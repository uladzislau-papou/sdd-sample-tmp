package com.example.contractmanagement.booking.inbound.driver

import com.example.contractmanagement.booking.core.domain.tourbooking.AvailableCapacity
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBookingStatus
import com.example.contractmanagement.booking.core.domain.tourbooking.event.TourBookingRequested
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.outport.AvailabilityChecker
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
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

/**
 * Use case tests for [RequestTourBookingDriver] (UC01).
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md`.
 */
class RequestTourBookingDriverTest {
    private val now: Instant = Instant.parse("2026-06-01T10:00:00Z")

    private val repository: TourBookingRepository = mock()
    private val availabilityChecker: AvailabilityChecker = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val clockPort: ClockPort = mock { on { now() } doReturn now }

    private val driver =
        RequestTourBookingDriver(repository, availabilityChecker, eventPublisher, clockPort)

    private val command =
        RequestTourBookingCommand(
            tourId = "tour-42",
            tourDate = LocalDate.parse("2026-07-01"),
            participantCount = 2,
            contactName = "Ada Lovelace",
            contactEmail = "ada@example.com",
        )

    private fun givenCapacity(value: Int) {
        whenever(availabilityChecker.checkAvailability(any(), any())).thenReturn(AvailableCapacity(value))
    }

    @Test
    fun request_returnsGeneratedIdAndRequestedStatus() {
        givenCapacity(10)

        val result = driver.request(command)

        assertThat(result.bookingId).isNotBlank()
        assertThat(result.status).isEqualTo(TourBookingStatus.REQUESTED.name)
    }

    @Test
    fun request_savesTheAggregate() {
        givenCapacity(10)

        driver.request(command)

        verify(repository).save(check { assertThat(it.status).isEqualTo(TourBookingStatus.REQUESTED) })
    }

    @Test
    fun request_usesClockPort_forTheEventTimestamp() {
        givenCapacity(10)

        driver.request(command)

        verify(eventPublisher).publish(
            check<TourBookingRequested> { assertThat(it.occurredAt).isEqualTo(now) },
        )
    }

    @Test
    fun request_publishesExactlyOneEvent() {
        givenCapacity(10)

        driver.request(command)

        verify(eventPublisher).publish(any())
    }

    @Test
    fun request_propagatesAvailabilityUnavailableException_andSavesNothing() {
        whenever(availabilityChecker.checkAvailability(any(), any()))
            .thenThrow(AvailabilityUnavailableException("availability system unreachable"))

        assertThatThrownBy { driver.request(command) }
            .isInstanceOf(AvailabilityUnavailableException::class.java)

        verify(repository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }

    @Test
    fun request_propagatesCapacityExceededException_andSavesNothing() {
        givenCapacity(1)

        assertThatThrownBy { driver.request(command) }
            .isInstanceOf(CapacityExceededException::class.java)

        verify(repository, never()).save(any())
        verify(eventPublisher, never()).publish(any())
    }
}
