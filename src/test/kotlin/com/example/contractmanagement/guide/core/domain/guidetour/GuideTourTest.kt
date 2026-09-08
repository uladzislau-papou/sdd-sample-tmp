package com.example.contractmanagement.guide.core.domain.guidetour

import com.example.contractmanagement.guide.core.domain.guidetour.exception.InvalidGuideTourStateException
import com.example.contractmanagement.guide.core.domain.guidetour.exception.TourStartTooEarlyException
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.shared.domain.event.TourStarted
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Domain tests for the [GuideTour] aggregate.
 *
 * SDD: see `documentation/domain/aggregate-guide-tour.spec.md`.
 */
class GuideTourTest {
    private val uuid: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val id = GuideTourId(uuid)
    private val tourId = TourId("TOUR-42")
    private val scheduledStart: Instant = Instant.parse("2026-06-01T09:00:00Z")
    private val onTime: Instant = Instant.parse("2026-06-01T09:05:00Z")

    private fun scheduled(): GuideTour = GuideTour.schedule(id, tourId, scheduledStart)

    private fun inState(
        status: GuideTourStatus,
        startedAt: Instant? = null,
    ): GuideTour =
        GuideTour.reconstitute(
            id = id,
            tourId = tourId,
            scheduledStart = scheduledStart,
            status = status,
            startedAt = startedAt,
        )

    @Test
    fun schedule_setsStatus_toScheduled() {
        assertThat(scheduled().status).isEqualTo(GuideTourStatus.SCHEDULED)
    }

    @Test
    fun schedule_recordsNoEvents() {
        assertThat(scheduled().pullDomainEvents()).isEmpty()
    }

    @Test
    fun schedule_leavesStartedAtAbsent() {
        assertThat(scheduled().startedAt).isNull()
    }

    @Test
    fun start_transitionsStatus_toRunning() {
        val tour = scheduled()

        tour.start(onTime)

        assertThat(tour.status).isEqualTo(GuideTourStatus.RUNNING)
    }

    @Test
    fun start_recordsTheActualStartTime() {
        val tour = scheduled()

        tour.start(onTime)

        assertThat(tour.startedAt).isEqualTo(onTime)
    }

    @Test
    fun start_publishesTourStarted_carryingTheIdentityAsAString() {
        val tour = scheduled()

        tour.start(onTime)

        assertThat(tour.pullDomainEvents())
            .containsExactly(TourStarted(uuid.toString(), tourId, onTime))
    }

    @Test
    fun start_succeeds_whenStartedExactlyAtTheScheduledTime() {
        val tour = scheduled()

        tour.start(scheduledStart)

        assertThat(tour.status).isEqualTo(GuideTourStatus.RUNNING)
    }

    @Test
    fun start_throwsTourStartTooEarlyException_whenBeforeTheScheduledTime() {
        val tour = scheduled()

        assertThatThrownBy { tour.start(scheduledStart.minusSeconds(1)) }
            .isInstanceOf(TourStartTooEarlyException::class.java)
    }

    @Test
    fun start_leavesStateUntouched_whenTooEarly() {
        val tour = scheduled()

        runCatching { tour.start(scheduledStart.minusSeconds(1)) }

        assertThat(tour.status).isEqualTo(GuideTourStatus.SCHEDULED)
        assertThat(tour.startedAt).isNull()
        assertThat(tour.pullDomainEvents()).isEmpty()
    }

    @Test
    fun start_throwsInvalidGuideTourStateException_whenAlreadyRunning() {
        val tour = inState(GuideTourStatus.RUNNING, startedAt = onTime)

        assertThatThrownBy { tour.start(onTime) }
            .isInstanceOf(InvalidGuideTourStateException::class.java)
    }

    @Test
    fun reconstitute_recordsNoEvents() {
        assertThat(inState(GuideTourStatus.RUNNING, onTime).pullDomainEvents()).isEmpty()
    }
}
