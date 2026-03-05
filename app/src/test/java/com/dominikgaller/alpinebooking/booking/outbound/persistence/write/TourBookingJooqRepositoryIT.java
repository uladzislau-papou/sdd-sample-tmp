package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static com.dominikgaller.alpinebooking.jooq.Tables.TOUR_BOOKING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Persistence integration tests for {@link TourBookingJooqRepository}.
 *
 * <p>Starts a minimal Spring context ({@link PersistenceTestApplication}) with H2 in-memory
 * database (profile {@code test}). Flyway migrations run automatically before each test. Each test method is rolled
 * back via {@link Transactional}.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@ActiveProfiles("test")
@Transactional
class TourBookingJooqRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    @Autowired
    private TourBookingJooqRepository repository;

    @Autowired
    private DSLContext dsl;

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_persistsAllFields() {
        final TourBooking booking = sampleBooking();

        repository.save(booking);

        final var record = dsl.selectFrom(TOUR_BOOKING)
                .where(TOUR_BOOKING.ID.eq(booking.bookingId().value().toString()))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getId()).isEqualTo(booking.bookingId().value().toString());
        assertThat(record.getTourId()).isEqualTo(booking.tourId().value());
        assertThat(record.getTourDate()).isEqualTo(booking.tourDate().value());
        assertThat(record.getParticipantCount()).isEqualTo(booking.participantCount().value());
        assertThat(record.getAvailableCapacity()).isEqualTo(booking.availableCapacity().value());
        assertThat(record.getContactName()).isEqualTo(booking.contact().name());
        assertThat(record.getContactEmail()).isEqualTo(booking.contact().email());
        assertThat(record.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void save_duplicateId_throwsDuplicateKeyException() {
        final TourBooking booking = sampleBooking();

        repository.save(booking);

        assertThatExceptionOfType(DuplicateKeyException.class)
                .isThrownBy(() -> repository.save(booking));
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenNotFound() {
        final BookingId unknownId = BookingId.generate();

        assertThat(repository.findById(unknownId)).isEmpty();
    }

    @Test
    void findById_returnsAggregate_afterSave() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        final var found = repository.findById(booking.bookingId());

        assertThat(found).isPresent();
        final TourBooking loaded = found.get();
        assertThat(loaded.bookingId()).isEqualTo(booking.bookingId());
        assertThat(loaded.tourId()).isEqualTo(booking.tourId());
        assertThat(loaded.tourDate()).isEqualTo(booking.tourDate());
        assertThat(loaded.participantCount()).isEqualTo(booking.participantCount());
        assertThat(loaded.availableCapacity()).isEqualTo(booking.availableCapacity());
        assertThat(loaded.contact()).isEqualTo(booking.contact());
        assertThat(loaded.status()).isEqualTo(TourBookingStatus.REQUESTED);
    }

    // ── update ───────────────────────────────────────────────────────────────

    @Test
    void update_changesStatus_inDatabase() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.confirm(NOW);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.CONFIRMED);
    }

    @Test
    void update_changesStatus_toCancelled_afterCancel() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.cancel(NOW);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TourBooking sampleBooking() {
        return TourBooking.request(
                BookingId.generate(),
                new TourId("TOUR-42"),
                new TourDate(FUTURE_DATE),
                new ParticipantCount(3),
                new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"),
                NOW);
    }
}
