package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.CancelledBy;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourDate;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplication;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CancelTourByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for UC12 – CancelTourByGuide, across both bounded contexts.
 *
 * <p>This is the only test that boots the <b>whole</b> application
 * ({@link AlpineBookingApplication}), and that is deliberate. Everything else in the suite
 * either stubs its collaborators or scopes component scanning to one package, so nothing
 * else would notice if the cross-context wiring failed to resolve —
 * {@link CancelTourByGuideDriver} needs {@code booking}'s
 * {@code MarkBookingCancelledByGuideUseCase} injected into a {@code guide} bean, which is
 * the one dependency in this codebase that spans contexts. A missing or ambiguous bean here
 * is a startup failure the unit tests cannot see.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The whole point is to observe what the
 * use case committed, so the test must not wrap it in a transaction that would both hide the
 * commit boundary and roll everything back afterwards. Each test therefore uses its own tour
 * and booking ids and leaves its rows behind; the schema is an in-memory H2 per run.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md} section 6,
 * and AC-01 / AC-07.
 */
@SpringBootTest(classes = AlpineBookingApplication.class)
@ActiveProfiles("test")
class CancelTourByGuideIT {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);
    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");

    @Autowired
    private CancelTourByGuideUseCase cancelTourByGuide;

    @Autowired
    private GuideTourRepository guideTourRepository;

    @Autowired
    private TourBookingRepository tourBookingRepository;

    /**
     * AC-01 end to end: one call cancels the tour in {@code guide} and its bookings in
     * {@code booking}, through the real inport rather than a mock.
     */
    @Test
    void cancel_cancelsTheTourAndItsBookingsAcrossBothContexts() {
        final TourId tourId = uniqueTourId();
        final GuideTour tour = savedTour(tourId);
        final TourBooking confirmed = savedBooking(tourId, TourBookingStatus.CONFIRMED);
        final TourBooking requested = savedBooking(tourId, TourBookingStatus.REQUESTED);

        final var result = cancelTourByGuide.cancel(
                new CancelTourByGuideCommand(
                        tour.id().value().toString(), "Severe weather warning"));

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.cancelledBookings()).isEqualTo(2);

        assertThat(guideTourRepository.findById(tour.id()))
                .get()
                .satisfies(reloaded ->
                        assertThat(reloaded.status()).isEqualTo(GuideTourStatus.CANCELLED));

        for (final TourBooking booking : new TourBooking[] {confirmed, requested}) {
            assertThat(tourBookingRepository.findById(booking.bookingId()))
                    .get()
                    .satisfies(reloaded -> {
                        assertThat(reloaded.status()).isEqualTo(TourBookingStatus.CANCELLED);
                        assertThat(reloaded.cancelledBy()).contains(CancelledBy.GUIDE);
                    });
        }
    }

    /**
     * A COMPLETED booking is skipped rather than blocking the cancellation — the criterion
     * lives in {@code findCancellableByTourId}. Asserted end to end because the unit test
     * uses a stub whose filtering I wrote; this one runs the real SQL predicate.
     */
    @Test
    void cancel_skipsTerminalBookings_andStillCancelsTheTour() {
        final TourId tourId = uniqueTourId();
        final GuideTour tour = savedTour(tourId);
        final TourBooking completed = savedBooking(tourId, TourBookingStatus.COMPLETED);

        final var result = cancelTourByGuide.cancel(
                new CancelTourByGuideCommand(tour.id().value().toString(), null));

        assertThat(result.cancelledBookings()).isZero();
        assertThat(guideTourRepository.findById(tour.id()))
                .get()
                .satisfies(t -> assertThat(t.status()).isEqualTo(GuideTourStatus.CANCELLED));
        assertThat(tourBookingRepository.findById(completed.bookingId()))
                .get()
                .satisfies(b -> assertThat(b.status()).isEqualTo(TourBookingStatus.COMPLETED));
    }

    /**
     * Bookings for a different tour must not be touched. Cheap to get wrong in a fan-out, and
     * invisible without a second tour present.
     */
    @Test
    void cancel_leavesBookingsOfOtherToursAlone() {
        final TourId tourId = uniqueTourId();
        final TourId otherTourId = uniqueTourId();
        final GuideTour tour = savedTour(tourId);
        final TourBooking mine = savedBooking(tourId, TourBookingStatus.CONFIRMED);
        final TourBooking theirs = savedBooking(otherTourId, TourBookingStatus.CONFIRMED);

        cancelTourByGuide.cancel(
                new CancelTourByGuideCommand(tour.id().value().toString(), null));

        assertThat(tourBookingRepository.findById(mine.bookingId()))
                .get().satisfies(b ->
                        assertThat(b.status()).isEqualTo(TourBookingStatus.CANCELLED));
        assertThat(tourBookingRepository.findById(theirs.bookingId()))
                .get().satisfies(b ->
                        assertThat(b.status()).isEqualTo(TourBookingStatus.CONFIRMED));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * A distinct {@code TourId} per test. Required because this class is not transactional,
     * so rows persist between tests and a shared id would let one test's bookings appear in
     * another's fan-out.
     */
    private static TourId uniqueTourId() {
        return new TourId("TOUR-" + UUID.randomUUID());
    }

    private GuideTour savedTour(final TourId tourId) {
        final GuideTour tour =
                GuideTour.schedule(GuideTourId.generate(), tourId, SCHEDULED_START);
        guideTourRepository.save(tour);
        return tour;
    }

    /**
     * A persisted booking in the requested state, reached through the real transitions rather
     * than {@code reconstitute}, so the row is one a running system could produce.
     */
    private TourBooking savedBooking(final TourId tourId, final TourBookingStatus status) {
        final TourBooking booking = TourBooking.request(
                BookingId.generate(), tourId, new TourDate(FUTURE_DATE),
                new ParticipantCount(3), new AvailableCapacity(10),
                new ParticipantContact("Alice", "alice@example.com"), NOW);
        tourBookingRepository.save(booking);

        switch (status) {
            case REQUESTED -> { }
            case CONFIRMED -> {
                booking.confirm(NOW);
                tourBookingRepository.update(booking);
            }
            case ACTIVE -> {
                booking.confirm(NOW);
                booking.markActive(NOW, "gt-1");
                tourBookingRepository.update(booking);
            }
            case COMPLETED -> {
                booking.confirm(NOW);
                booking.markActive(NOW, "gt-1");
                booking.markCompleted(NOW, "gt-1");
                tourBookingRepository.update(booking);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported seed status: " + status);
        }
        booking.pullDomainEvents();
        return booking;
    }
}
