package com.example.contractmanagement.booking.inbound.listener

import com.example.contractmanagement.booking.core.domain.tourbooking.AvailableCapacity
import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantContact
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantCount
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBooking
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBookingStatus
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.booking.core.inport.command.MarkBookingActiveCommand
import com.example.contractmanagement.booking.core.inport.result.MarkBookingActiveResult
import com.example.contractmanagement.booking.core.inport.usecase.MarkBookingActiveUseCase
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.shared.domain.event.TourStarted
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Use case tests for [TourStartedListener] (UC06).
 *
 * The listener is the **only** trigger for `CONFIRMED -> ACTIVE`; there is no endpoint for
 * direct activation, which is why UC06 has no `api/` file. These tests therefore stand in
 * for the API tests the other use cases have.
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md`.
 */
class TourStartedListenerTest {
    private val tourId = TourId("TOUR-42")
    private val startedAt: Instant = Instant.parse("2026-06-01T09:05:00Z")
    private val guideTourId = "33333333-3333-3333-3333-333333333333"

    private val repository: TourBookingRepository = mock()
    private val markBookingActiveUseCase: MarkBookingActiveUseCase = mock()

    private val listener = TourStartedListener(repository, markBookingActiveUseCase)

    private val event = TourStarted(guideTourId, tourId, startedAt)

    private fun confirmedBooking(id: UUID): TourBooking =
        TourBooking.reconstitute(
            bookingId = BookingId.of(id),
            tourId = tourId,
            tourDate = TourDate(LocalDate.parse("2026-07-01")),
            participantCount = ParticipantCount(2),
            availableCapacity = AvailableCapacity(10),
            contact = ParticipantContact("Ada Lovelace", "ada@example.com"),
            status = TourBookingStatus.CONFIRMED,
        )

    @Test
    fun onTourStarted_activatesEveryConfirmedBookingForThatTour() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        whenever(repository.findConfirmedByTourId(tourId))
            .thenReturn(listOf(confirmedBooking(first), confirmedBooking(second)))
        whenever(markBookingActiveUseCase.markActive(any()))
            .thenReturn(MarkBookingActiveResult(TourBookingStatus.ACTIVE.name))

        listener.onTourStarted(event)

        val captor = argumentCaptor<MarkBookingActiveCommand>()
        verify(markBookingActiveUseCase, times(2)).markActive(captor.capture())
        assertThat(captor.allValues.map { it.bookingId })
            .containsExactly(first.toString(), second.toString())
    }

    @Test
    fun onTourStarted_relaysTheEventTimestampAndTheGuideTourId() {
        whenever(repository.findConfirmedByTourId(tourId))
            .thenReturn(listOf(confirmedBooking(UUID.randomUUID())))
        whenever(markBookingActiveUseCase.markActive(any()))
            .thenReturn(MarkBookingActiveResult(TourBookingStatus.ACTIVE.name))

        listener.onTourStarted(event)

        val captor = argumentCaptor<MarkBookingActiveCommand>()
        verify(markBookingActiveUseCase).markActive(captor.capture())
        assertThat(captor.firstValue.startedAt).isEqualTo(startedAt)
        assertThat(captor.firstValue.guideTourId).isEqualTo(guideTourId)
    }

    @Test
    fun onTourStarted_doesNothing_whenNoBookingIsConfirmed() {
        whenever(repository.findConfirmedByTourId(tourId)).thenReturn(emptyList())

        listener.onTourStarted(event)

        verify(markBookingActiveUseCase, never()).markActive(any())
    }
}
