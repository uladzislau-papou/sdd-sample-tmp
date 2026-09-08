package com.example.contractmanagement.guide.outbound.persistence

import com.example.contractmanagement.guide.core.domain.guidetour.GuideTour
import com.example.contractmanagement.guide.core.domain.guidetour.GuideTourId
import com.example.contractmanagement.guide.core.domain.guidetour.GuideTourStatus
import com.example.contractmanagement.guide.core.outport.GuideTourRepository
import com.example.contractmanagement.shared.domain.TourId
import com.example.contractmanagement.support.PostgresIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

/**
 * Adapter integration test for [GuideTourJpaRepository] against a real PostgreSQL.
 *
 * SDD: adapter integration test per `documentation/test.definition.md` § 2.3.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
@Import(GuideTourJpaRepository::class)
class GuideTourJpaRepositoryIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var repository: GuideTourRepository

    private val scheduledStart: Instant = Instant.parse("2026-06-01T09:00:00Z")

    private fun scheduled(id: UUID) = GuideTour.schedule(GuideTourId(id), TourId("TOUR-42"), scheduledStart)

    @Test
    fun save_thenFindById_roundTripsEveryField() {
        val id = UUID.randomUUID()
        repository.save(scheduled(id))

        val loaded = requireNotNull(repository.findById(GuideTourId(id)))

        assertThat(loaded.id).isEqualTo(GuideTourId(id))
        assertThat(loaded.tourId).isEqualTo(TourId("TOUR-42"))
        assertThat(loaded.scheduledStart).isEqualTo(scheduledStart)
        assertThat(loaded.status).isEqualTo(GuideTourStatus.SCHEDULED)
        assertThat(loaded.startedAt).isNull()
    }

    @Test
    fun findById_returnsNull_whenNoTourHasThatIdentity() {
        assertThat(repository.findById(GuideTourId.generate())).isNull()
    }

    @Test
    fun update_persistsTheTransition_andTheStartTime() {
        val id = UUID.randomUUID()
        val startedAt = scheduledStart.plusSeconds(300)
        repository.save(scheduled(id))

        val loaded = requireNotNull(repository.findById(GuideTourId(id)))
        loaded.start(startedAt)
        repository.update(loaded)

        val reloaded = requireNotNull(repository.findById(GuideTourId(id)))
        assertThat(reloaded.status).isEqualTo(GuideTourStatus.RUNNING)
        assertThat(reloaded.startedAt).isEqualTo(startedAt)
    }

    @Test
    fun startedAt_staysNull_forATourThatWasNeverStarted() {
        // A null start time must remain distinguishable from an epoch one: the mapper
        // converts through LocalDateTime, and a careless default would silently invent a
        // start.
        val id = UUID.randomUUID()
        repository.save(scheduled(id))

        assertThat(requireNotNull(repository.findById(GuideTourId(id))).startedAt).isNull()
    }
}
