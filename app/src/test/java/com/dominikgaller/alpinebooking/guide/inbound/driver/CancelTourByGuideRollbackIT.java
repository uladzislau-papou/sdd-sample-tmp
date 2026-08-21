package com.dominikgaller.alpinebooking.guide.inbound.driver;

import com.dominikgaller.alpinebooking.booking.core.inport.usecase.MarkBookingCancelledByGuideUseCase;
import com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplication;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTour;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CancelTourByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.outport.GuideTourRepository;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UC12 AC-07: when the booking side fails, the tour cancellation must not commit.
 *
 * <p>This is the test that justifies the whole design. UC09 is a synchronous inport call
 * joining the caller's transaction rather than a domain event, and the only reason to accept
 * that coupling is this guarantee: a tour is never reported cancelled while its bookings
 * still believe it is going ahead. Without this test, the guarantee is a claim in a spec —
 * `@Transactional` with the wrong propagation, or an exception swallowed anywhere in the
 * chain, would silently break it while every unit test kept passing.
 *
 * <p>The booking inport is replaced by a mock that throws. That is the right seam: what is
 * under test is `guide`'s transactional behaviour when the callee fails, not any particular
 * way `booking` can fail. Using the real inport would mean contriving a database error, which
 * would test H2 rather than the rollback.
 *
 * <p>Deliberately not `@Transactional`. The reload must happen outside the failed
 * transaction, or it would be reading state the rollback had not yet discarded.
 *
 * <p>Verified by mutation: removing `@Transactional` from `CancelTourByGuideDriver` fails
 * `cancel_rollsBackTheTourCancellation_whenTheBookingSideFails`, so the assertion is
 * load-bearing rather than incidentally true.
 *
 * <p>An earlier revision of this comment also claimed that annotating *this class*
 * `@Transactional` would mask the defect. That was asserted without being run, and it is
 * false: with a test-managed transaction the reload happens inside it and still observes
 * CANCELLED rather than SCHEDULED, so the test fails either way. The real reason to omit
 * `@Transactional` is the one above, not a masking risk.
 *
 * <p>Separate class from {@link CancelTourByGuideIT} because `@MockitoBean` is class-scoped
 * and that test needs the real booking side.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md} § 6, AC-07.
 */
@SpringBootTest(classes = AlpineBookingApplication.class)
@ActiveProfiles("test")
class CancelTourByGuideRollbackIT {

    private static final Instant SCHEDULED_START = Instant.parse("2026-06-15T09:00:00Z");

    @Autowired
    private CancelTourByGuideUseCase cancelTourByGuide;

    @Autowired
    private GuideTourRepository guideTourRepository;

    @MockitoBean
    private MarkBookingCancelledByGuideUseCase markBookingCancelledByGuide;

    @Test
    void cancel_rollsBackTheTourCancellation_whenTheBookingSideFails() {
        when(markBookingCancelledByGuide.cancelByGuide(any()))
                .thenThrow(new IllegalStateException("booking side unavailable"));
        final GuideTour tour = savedScheduledTour();

        assertThatExceptionOfType(BookingCancellationFailedException.class)
                .isThrownBy(() -> cancelTourByGuide.cancel(
                        new CancelTourByGuideCommand(
                                tour.id().value().toString(), "Severe weather warning")));

        // Reloaded outside the failed transaction: the row must be untouched.
        assertThat(guideTourRepository.findById(tour.id()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.status()).isEqualTo(GuideTourStatus.SCHEDULED);
                    assertThat(reloaded.cancelledAt()).isEmpty();
                    assertThat(reloaded.cancellationReason()).isEmpty();
                });
    }

    /**
     * The 502 must carry the original failure. A rolled-back transaction leaves nothing else
     * to diagnose from, and a bare "booking cancellation failed" would send whoever is paged
     * looking in the wrong context.
     */
    @Test
    void cancel_preservesTheUnderlyingCauseOnTheFailure() {
        final IllegalStateException cause = new IllegalStateException("booking side unavailable");
        when(markBookingCancelledByGuide.cancelByGuide(any())).thenThrow(cause);
        final GuideTour tour = savedScheduledTour();

        assertThatExceptionOfType(BookingCancellationFailedException.class)
                .isThrownBy(() -> cancelTourByGuide.cancel(
                        new CancelTourByGuideCommand(tour.id().value().toString(), null)))
                .withCause(cause);
    }

    private GuideTour savedScheduledTour() {
        final GuideTour tour = GuideTour.schedule(
                GuideTourId.generate(),
                new TourId("TOUR-" + UUID.randomUUID()),
                SCHEDULED_START);
        guideTourRepository.save(tour);
        return tour;
    }
}
