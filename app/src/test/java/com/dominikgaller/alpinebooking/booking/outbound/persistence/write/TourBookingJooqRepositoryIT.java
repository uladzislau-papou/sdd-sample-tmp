package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancellationReason;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancelledBy;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

        booking.cancel(NOW, CancelledBy.USER, null);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * UC04 — the participant count is mutable domain state, so its round-trip needs its
     * own assertion. The other update tests only cover {@code status}.
     */
    @Test
    void update_changesParticipantCount_inDatabase() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.changeParticipants(new ParticipantCount(7), new AvailableCapacity(50), NOW);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().participantCount()).isEqualTo(new ParticipantCount(7));
    }

    /**
     * UC04 — {@code changeParticipants} also refreshes {@code availableCapacity}
     * ({@code TourBooking:203}), so that field is mutable domain state too and must
     * survive an update. Same defect class as the participant count.
     */
    @Test
    void update_changesAvailableCapacity_inDatabase() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.changeParticipants(new ParticipantCount(7), new AvailableCapacity(42), NOW);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().availableCapacity()).isEqualTo(new AvailableCapacity(42));
    }

    /**
     * UC06 — the ACTIVE transition round-trip.
     *
     * <p>Only {@code status} is asserted, and deliberately so: {@code TourBooking} holds
     * no {@code startedAt} or {@code guideTourId} field. {@code markActive(Instant, String)}
     * takes both purely as {@code BookingActivated} event payload, so there is nothing
     * further to persist and {@code tour_booking} correctly has no such columns.
     */
    @Test
    void update_changesStatus_toActive_afterMarkActive() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);
        booking.confirm(NOW);
        repository.update(booking);

        booking.markActive(NOW, "guide-tour-1");
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.ACTIVE);
    }

    /**
     * UC06 — the activation fan-out needs the CONFIRMED bookings for a tour. Expressing
     * that as a query keeps the "which bookings are candidates" criterion out of
     * {@code TourStartedListener}, which {@code architecture.definition.md} section 4.8
     * forbids from holding business logic. The aggregate still guards the transition.
     */
    @Test
    void findConfirmedByTourId_returnsOnlyConfirmedBookingsForThatTour() {
        final TourBooking confirmed = sampleBooking();
        repository.save(confirmed);
        confirmed.confirm(NOW);
        repository.update(confirmed);

        final TourBooking stillRequested = sampleBooking();
        repository.save(stillRequested);

        final TourBooking cancelled = sampleBooking();
        repository.save(cancelled);
        cancelled.cancel(NOW, CancelledBy.USER, null);
        repository.update(cancelled);

        final var found = repository.findConfirmedByTourId(confirmed.tourId());

        assertThat(found).extracting(b -> b.bookingId().value().toString())
                .containsExactly(confirmed.bookingId().value().toString());
    }

    @Test
    void findConfirmedByTourId_returnsEmpty_whenNoConfirmedBookingsExist() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        assertThat(repository.findConfirmedByTourId(booking.tourId())).isEmpty();
    }

    /**
     * UC07 — the COMPLETED transition round-trip.
     *
     * <p>Asserts {@code status} only, for the same reason as the ACTIVE case above:
     * {@code TourBooking} holds no {@code completedAt} field. {@code markCompleted(Instant, String)}
     * takes the timestamp purely as {@code BookingCompleted} payload, so {@code tour_booking}
     * correctly has no such column — see
     * {@code documentation/use-cases/uc07-mark-booking-completed.spec.md} section 6.
     */
    @Test
    void update_changesStatus_toCompleted_afterMarkCompleted() {
        final TourBooking booking = activeBooking();

        booking.markCompleted(NOW, null);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.COMPLETED);
    }

    /**
     * UC07 — the completion fan-out queries ACTIVE bookings, mirroring UC06's CONFIRMED
     * query. Without this test the {@code STATUS.eq(ACTIVE)} predicate is only exercised
     * by the listener's in-memory stub, which cannot catch a wrong column or a wrong
     * literal in the generated SQL.
     */
    @Test
    void findActiveByTourId_returnsOnlyActiveBookingsForThatTour() {
        final TourBooking active = activeBooking();

        final TourBooking confirmed = sampleBooking();
        repository.save(confirmed);
        confirmed.confirm(NOW);
        repository.update(confirmed);

        final TourBooking completed = activeBooking();
        completed.markCompleted(NOW, null);
        repository.update(completed);

        final var found = repository.findActiveByTourId(active.tourId());

        assertThat(found).extracting(b -> b.bookingId().value().toString())
                .containsExactly(active.bookingId().value().toString());
    }

    @Test
    void findActiveByTourId_returnsEmpty_whenNoActiveBookingsExist() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        assertThat(repository.findActiveByTourId(booking.tourId())).isEmpty();
    }

    /**
     * UC08 — the schema's column width and the domain's ceiling must not drift apart.
     *
     * <p>`CancellationReason.MAX_LENGTH` is 400 and `cancellation_reason` is `VARCHAR(400)`,
     * and nothing but this test connects the two. Raising the constant alone would produce
     * either a silent truncation or a driver-level error at write time depending on the
     * database, long after the change looked correct — and the domain would be the side that
     * looked right. Comparing them against the generated jOOQ metadata makes the divergence
     * a build failure instead.
     *
     * <p>Deliberately asserts equality rather than "column is at least MAX_LENGTH": a column
     * wider than the ceiling is also a defect, because it means the schema tolerates values
     * the domain rejects, and someone will eventually read that as the real limit.
     */
    @Test
    void cancellationReasonColumnWidth_matchesTheDomainCeiling() {
        assertThat(TOUR_BOOKING.CANCELLATION_REASON.getDataType().length())
                .isEqualTo(CancellationReason.MAX_LENGTH);
    }

    /**
     * UC08 — cancellation attribution must survive a round trip. This is the assertion the
     * port spec's standing obligation demands for three newly mutable fields; without it
     * the columns could be absent from the `update` statement and every other test would
     * still pass, which is exactly how UC04's effect was lost.
     */
    @Test
    void update_persistsCancellationAttribution() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.cancel(NOW, CancelledBy.USER, new CancellationReason("Travel plans changed"));
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().status()).isEqualTo(TourBookingStatus.CANCELLED);
        assertThat(reloaded.get().cancelledBy()).contains(CancelledBy.USER);
        assertThat(reloaded.get().cancelledAt()).contains(NOW);
        assertThat(reloaded.get().cancellationReason())
                .contains(new CancellationReason("Travel plans changed"));
    }

    /**
     * UC08 — cancelling without a reason is permitted, so the column stays null rather than
     * holding an empty string. Asserted separately because a mapper that coerced null to ""
     * would satisfy the test above and silently invent a reason nobody gave.
     */
    @Test
    void update_persistsCancellation_withoutReason() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.cancel(NOW, CancelledBy.USER, null);
        repository.update(booking);

        final var reloaded = repository.findById(booking.bookingId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().cancellationReason()).isEmpty();
        assertThat(reloaded.get().cancelledBy()).contains(CancelledBy.USER);
    }

    /**
     * UC08 — a live booking must come back with all three cancellation fields empty. Guards
     * the mapper's null handling in the other direction: a non-null default would make every
     * booking look cancelled.
     */
    @Test
    void findById_returnsEmptyCancellationFields_forALiveBooking() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        final var reloaded = repository.findById(booking.bookingId());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().cancelledAt()).isEmpty();
        assertThat(reloaded.get().cancelledBy()).isEmpty();
        assertThat(reloaded.get().cancellationReason()).isEmpty();
    }

    /**
     * UC08 — `cancelled_at` is the first `Instant` column on `tour_booking`, so it pins the
     * UTC convention the guide side already follows
     * (`GuideTourJooqRepositoryIT.save_persistsScheduledStart_asUtcLocalDateTime`). Storing
     * local time would make the value depend on the server's zone.
     */
    @Test
    void update_persistsCancelledAt_asUtcLocalDateTime() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);

        booking.cancel(NOW, CancelledBy.USER, null);
        repository.update(booking);

        final var record = dsl.selectFrom(TOUR_BOOKING)
                .where(TOUR_BOOKING.ID.eq(booking.bookingId().value().toString()))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getCancelledAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    /**
     * A persisted booking in ACTIVE state — the precondition for UC07. Reaches it through
     * the real transitions ({@code confirm} → {@code markActive}) rather than
     * {@code reconstitute}, so the row is one a running system could actually produce.
     */
    private TourBooking activeBooking() {
        final TourBooking booking = sampleBooking();
        repository.save(booking);
        booking.confirm(NOW);
        booking.markActive(NOW, "guide-tour-1");
        repository.update(booking);
        return booking;
    }

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
