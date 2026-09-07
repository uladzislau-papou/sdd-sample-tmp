package com.example.service.guide.inbound.driver

import com.example.service.guide.core.domain.guidetour.GuideTour
import com.example.service.guide.core.domain.guidetour.GuideTourId
import com.example.service.guide.core.domain.guidetour.GuideTourStatus
import com.example.service.guide.core.domain.guidetour.exception.GuideTourNotFoundException
import com.example.service.guide.core.domain.guidetour.exception.TourStartTooEarlyException
import com.example.service.guide.core.inport.command.StartTourCommand
import com.example.service.guide.core.outport.GuideTourRepository
import com.example.service.shared.domain.TourId
import com.example.service.shared.domain.event.TourStarted
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
import java.util.UUID

/**
 * Use case tests for [StartTourDriver] (UC05).
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md`.
 */
class StartTourDriverTest {
    private val uuid: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val scheduledStart: Instant = Instant.parse("2026-06-01T09:00:00Z")
    private val clockNow: Instant = Instant.parse("2026-06-01T09:05:00Z")
    private val explicitStart: Instant = Instant.parse("2026-06-01T09:10:00Z")

    private val repository: GuideTourRepository = mock()
    private val eventPublisher: DomainEventPublisher = mock()
    private val clockPort: ClockPort = mock { on { now() } doReturn clockNow }

    private val driver = StartTourDriver(repository, eventPublisher, clockPort)

    private fun command(startedAt: Instant? = null) = StartTourCommand(uuid.toString(), startedAt)

    private fun scheduledTour(): GuideTour = GuideTour.schedule(GuideTourId(uuid), TourId("TOUR-42"), scheduledStart)

    @Test
    fun start_returnsRunningStatus() {
        whenever(repository.findById(any())).thenReturn(scheduledTour())

        assertThat(driver.start(command()).status).isEqualTo(GuideTourStatus.RUNNING.name)
    }

    @Test
    fun start_updatesTheAggregate() {
        whenever(repository.findById(any())).thenReturn(scheduledTour())

        driver.start(command())

        verify(repository).update(check { assertThat(it.status).isEqualTo(GuideTourStatus.RUNNING) })
    }

    @Test
    fun start_usesClockPort_whenTheCommandCarriesNoTimestamp() {
        whenever(repository.findById(any())).thenReturn(scheduledTour())

        driver.start(command())

        verify(eventPublisher).publish(check<TourStarted> { assertThat(it.startedAt).isEqualTo(clockNow) })
    }

    @Test
    fun start_prefersTheCommandTimestamp_overTheClock() {
        whenever(repository.findById(any())).thenReturn(scheduledTour())

        driver.start(command(startedAt = explicitStart))

        verify(eventPublisher).publish(check<TourStarted> { assertThat(it.startedAt).isEqualTo(explicitStart) })
    }

    @Test
    fun start_throwsGuideTourNotFoundException_whenNoTourHasThatIdentity() {
        whenever(repository.findById(any())).thenReturn(null)

        assertThatThrownBy { driver.start(command()) }
            .isInstanceOf(GuideTourNotFoundException::class.java)
    }

    @Test
    fun start_propagatesTourStartTooEarlyException_andPersistsNothing() {
        whenever(repository.findById(any())).thenReturn(scheduledTour())

        assertThatThrownBy { driver.start(command(startedAt = scheduledStart.minusSeconds(1))) }
            .isInstanceOf(TourStartTooEarlyException::class.java)

        verify(repository, never()).update(any())
        verify(eventPublisher, never()).publish(any())
    }
}
