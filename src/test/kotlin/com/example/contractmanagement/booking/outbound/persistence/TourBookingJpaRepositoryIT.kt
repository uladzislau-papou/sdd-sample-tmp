package com.example.contractmanagement.booking.outbound.persistence

import com.example.contractmanagement.booking.core.domain.tourbooking.AvailableCapacity
import com.example.contractmanagement.booking.core.domain.tourbooking.BookingId
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantContact
import com.example.contractmanagement.booking.core.domain.tourbooking.ParticipantCount
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBooking
import com.example.contractmanagement.booking.core.domain.tourbooking.TourBookingStatus
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.booking.core.outport.TourBookingRepository
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
import java.time.LocalDate
import java.util.UUID

/**
 * Adapter integration test for [TourBookingJpaRepository] against a real PostgreSQL.
 *
 * `replace = NONE` because `@DataJpaTest` would otherwise swap in an embedded database and
 * quietly undo the point of the test. Flyway is imported explicitly: `@DataJpaTest` does
 * not auto-configure it, and with `ddl-auto: validate` the schema has to come from the
 * migrations — which means this test also proves the migrations and the entity mapping
 * agree. That agreement is the single most common thing to break and the least likely to
 * be noticed by a unit test.
 *
 * SDD: adapter integration test per `documentation/test.definition.md` § 2.3, and
 * `documentation/ports/tour-booking-repository.outport.spec.md`.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
@Import(TourBookingJpaRepository::class)
class TourBookingJpaRepositoryIT : PostgresIntegrationTest() {
    @Autowired
    private lateinit var repository: TourBookingRepository

    private fun booking(
        id: UUID = UUID.randomUUID(),
        status: TourBookingStatus = TourBookingStatus.REQUESTED,
        participantCount: Int = 2,
        availableCapacity: Int = 10,
    ): TourBooking =
        TourBooking.reconstitute(
            bookingId = BookingId.of(id),
            tourId = TourId("TOUR-42"),
            tourDate = TourDate(LocalDate.parse("2026-07-01")),
            participantCount = ParticipantCount(participantCount),
            availableCapacity = AvailableCapacity(availableCapacity),
            contact = ParticipantContact("Ada Lovelace", "ada@example.com"),
            status = status,
        )

    @Test
    fun save_thenFindById_roundTripsEveryField() {
        val id = UUID.randomUUID()
        repository.save(booking(id = id))

        val loaded = repository.findById(BookingId.of(id))

        assertThat(loaded).isNotNull
        requireNotNull(loaded)
        assertThat(loaded.bookingId).isEqualTo(BookingId.of(id))
        assertThat(loaded.tourId).isEqualTo(TourId("TOUR-42"))
        assertThat(loaded.tourDate).isEqualTo(TourDate(LocalDate.parse("2026-07-01")))
        assertThat(loaded.participantCount).isEqualTo(ParticipantCount(2))
        assertThat(loaded.availableCapacity).isEqualTo(AvailableCapacity(10))
        assertThat(loaded.contact).isEqualTo(ParticipantContact("Ada Lovelace", "ada@example.com"))
        assertThat(loaded.status).isEqualTo(TourBookingStatus.REQUESTED)
    }

    @Test
    fun findById_returnsNull_whenNoBookingHasThatIdentity() {
        assertThat(repository.findById(BookingId.generate())).isNull()
    }

    @Test
    fun update_persistsTheStatusTransition() {
        val id = UUID.randomUUID()
        repository.save(booking(id = id))

        val loaded = requireNotNull(repository.findById(BookingId.of(id)))
        loaded.confirm(java.time.Instant.parse("2026-06-01T10:00:00Z"))
        repository.update(loaded)

        assertThat(requireNotNull(repository.findById(BookingId.of(id))).status)
            .isEqualTo(TourBookingStatus.CONFIRMED)
    }

    @Test
    fun update_writesEveryMutableField_notOnlyTheOneTheUseCaseChanged() {
        // The regression this exists for: a hand-written UPDATE listed only `status`, so an
        // entire use case's effect was discarded while its own tests stayed green. Asserting
        // one field per test is what let that through, so this test asserts all of them
        // after a change to one.
        val id = UUID.randomUUID()
        repository.save(booking(id = id, participantCount = 2, availableCapacity = 10))

        val loaded = requireNotNull(repository.findById(BookingId.of(id)))
        loaded.confirm(java.time.Instant.parse("2026-06-01T10:00:00Z"))
        repository.update(loaded)

        val reloaded = requireNotNull(repository.findById(BookingId.of(id)))
        assertThat(reloaded.status).isEqualTo(TourBookingStatus.CONFIRMED)
        assertThat(reloaded.participantCount).isEqualTo(ParticipantCount(2))
        assertThat(reloaded.availableCapacity).isEqualTo(AvailableCapacity(10))
        assertThat(reloaded.contact).isEqualTo(ParticipantContact("Ada Lovelace", "ada@example.com"))
    }
}
