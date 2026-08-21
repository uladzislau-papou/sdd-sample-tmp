package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.shared.domain.event.DomainEvent;
import com.dominikgaller.alpinebooking.shared.domain.TourId;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingActivated;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCompleted;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.ParticipantsChanged;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCancelledByGuide;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.BookingCancelledByUser;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.TourBookingConfirmed;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.event.TourBookingRequested;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TourBookingTest {

    private static final Instant NOW = Instant.parse("2026-03-03T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-03-04T12:00:00Z");
    private static final CancellationReason REASON =
            new CancellationReason("Travel plans changed");
    private static final String GUIDE_TOUR_ID = "GT-77";
    private static final CancellationReason OTHER_REASON =
            new CancellationReason("Guide fell ill");
    private static final LocalDate FUTURE_DATE = LocalDate.of(2026, 6, 15);

    private static final BookingId BOOKING_ID = BookingId.generate();
    private static final TourId TOUR_ID = new TourId("TOUR-42");
    private static final TourDate TOUR_DATE = new TourDate(FUTURE_DATE);
    private static final ParticipantCount COUNT_2 = new ParticipantCount(2);
    private static final AvailableCapacity CAPACITY_10 = new AvailableCapacity(10);
    private static final ParticipantContact CONTACT =
            new ParticipantContact("Alice", "alice@example.com");

    private TourBooking validBooking() {
        return TourBooking.request(BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT, NOW);
    }

    // ── UC01: request factory ────────────────────────────────────────────────

    @Test
    void requestSetsStatusToRequested() {
        assertThat(validBooking().status()).isEqualTo(TourBookingStatus.REQUESTED);
    }

    @Test
    void requestStoresAllFieldsCorrectly() {
        final TourBooking booking = validBooking();
        assertThat(booking.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(booking.tourId()).isEqualTo(TOUR_ID);
        assertThat(booking.tourDate()).isEqualTo(TOUR_DATE);
        assertThat(booking.participantCount()).isEqualTo(COUNT_2);
        assertThat(booking.availableCapacity()).isEqualTo(CAPACITY_10);
        assertThat(booking.contact()).isEqualTo(CONTACT);
    }

    @Test
    void pullDomainEventsReturnsExactlyOneTourBookingRequested() {
        final TourBooking booking = validBooking();
        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TourBookingRequested.class);
        final TourBookingRequested event = (TourBookingRequested) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.tourId()).isEqualTo(TOUR_ID);
        assertThat(event.tourDate()).isEqualTo(TOUR_DATE);
        assertThat(event.participantCount()).isEqualTo(COUNT_2);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void pullDomainEventsCalledTwiceReturnsEmptyListOnSecondCall() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents();
        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    @Test
    void pastTourDateThrowsInvalidBookingRequestException() {
        final TourDate pastDate = new TourDate(LocalDate.of(2025, 1, 1));
        assertThatExceptionOfType(InvalidBookingRequestException.class)
                .isThrownBy(() -> TourBooking.request(
                        BOOKING_ID, TOUR_ID, pastDate, COUNT_2, CAPACITY_10, CONTACT, NOW));
    }

    @Test
    void participantCountExceedingCapacityThrowsCapacityExceededException() {
        final ParticipantCount tooMany = new ParticipantCount(11);
        assertThatExceptionOfType(CapacityExceededException.class)
                .isThrownBy(() -> TourBooking.request(
                        BOOKING_ID, TOUR_ID, TOUR_DATE, tooMany, CAPACITY_10, CONTACT, NOW));
    }

    @Test
    void participantCountEqualToCapacityIsValid() {
        final ParticipantCount exactCount = new ParticipantCount(10);
        final TourBooking booking = TourBooking.request(
                BOOKING_ID, TOUR_ID, TOUR_DATE, exactCount, CAPACITY_10, CONTACT, NOW);
        assertThat(booking.status()).isEqualTo(TourBookingStatus.REQUESTED);
    }

    // ── UC02: confirm ────────────────────────────────────────────────────────

    @Test
    void confirm_transitionsStatusToConfirmed() {
        final TourBooking booking = validBooking();

        booking.confirm(NOW);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CONFIRMED);
    }

    @Test
    void confirm_publishesTourBookingConfirmedEvent() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents(); // drain TourBookingRequested

        booking.confirm(NOW);

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TourBookingConfirmed.class);
        final TourBookingConfirmed event = (TourBookingConfirmed) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void confirm_throwsInvalidBookingStateException_whenAlreadyConfirmed() {
        final TourBooking booking = validBooking();
        booking.confirm(NOW);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.confirm(NOW));
    }

    @Test
    void pullDomainEvents_returnsOnlyConfirmedEvent_afterPullingRequestedAndCallingConfirm() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents(); // drain the initial TourBookingRequested

        booking.confirm(NOW);

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(TourBookingConfirmed.class);
    }

    // ── UC03: cancel ─────────────────────────────────────────────────────────

    @Test
    void cancel_fromRequested_transitionsToCancelled() {
        final TourBooking booking = validBooking();

        booking.cancel(NOW, CancelledBy.USER, null);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    @Test
    void cancel_fromConfirmed_transitionsToCancelled() {
        final TourBooking booking = validBooking();
        booking.confirm(NOW);

        booking.cancel(NOW, CancelledBy.USER, null);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    @Test
    void cancel_fromActive_throwsInvalidBookingStateException() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.ACTIVE);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.USER, null));
    }

    @Test
    void cancel_fromCompleted_throwsInvalidBookingStateException() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.COMPLETED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.USER, null));
    }

    @Test
    void cancel_fromCancelled_throwsInvalidBookingStateException() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CANCELLED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.USER, null));
    }

    // ── UC08: cancel attribution ─────────────────────────────────────────────

    @Test
    void cancel_byUser_publishesBookingCancelledByUserEvent() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents(); // drain TourBookingRequested

        booking.cancel(NOW, CancelledBy.USER, REASON);

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(BookingCancelledByUser.class);
        final BookingCancelledByUser event = (BookingCancelledByUser) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.cancelledAt()).isEqualTo(NOW);
        assertThat(event.reason()).isEqualTo(REASON);
    }

    @Test
    void cancel_byUser_fromRequested_recordsUserAttribution() {
        final TourBooking booking = validBooking();

        booking.cancel(NOW, CancelledBy.USER, REASON);

        assertThat(booking.cancelledBy()).contains(CancelledBy.USER);
        assertThat(booking.cancelledAt()).contains(NOW);
    }

    @Test
    void cancel_byUser_fromConfirmed_recordsUserAttribution() {
        final TourBooking booking = validBooking();
        booking.confirm(NOW);

        booking.cancel(NOW, CancelledBy.USER, REASON);

        assertThat(booking.cancelledBy()).contains(CancelledBy.USER);
    }

    @Test
    void cancel_byUser_recordsReason() {
        final TourBooking booking = validBooking();

        booking.cancel(NOW, CancelledBy.USER, REASON);

        assertThat(booking.cancellationReason()).contains(REASON);
    }

    @Test
    void cancel_withoutReason_leavesReasonEmpty() {
        final TourBooking booking = validBooking();

        booking.cancel(NOW, CancelledBy.USER, null);

        assertThat(booking.cancellationReason()).isEmpty();
        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    @Test
    void cancellationFields_areEmpty_beforeCancellation() {
        final TourBooking booking = validBooking();

        assertThat(booking.cancelledAt()).isEmpty();
        assertThat(booking.cancelledBy()).isEmpty();
        assertThat(booking.cancellationReason()).isEmpty();
    }

    /**
     * UC08 AC-06. A participant cancelling an already-cancelled booking gets a 409, and the
     * original record survives. The second attempt is deliberately also a {@code USER}: a
     * second <em>guide</em> cancellation is an idempotent no-op instead of a throw
     * (UC09 AC-04), so using {@code GUIDE} here would assert the wrong contract — as an
     * earlier revision of this test did, and it started failing the moment UC09 landed.
     */
    @Test
    void cancel_whenAlreadyCancelled_doesNotOverwriteAttribution() {
        final TourBooking booking = validBooking();
        booking.cancel(NOW, CancelledBy.USER, REASON);
        booking.pullDomainEvents();

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(LATER, CancelledBy.USER, OTHER_REASON));

        assertThat(booking.cancelledBy()).contains(CancelledBy.USER);
        assertThat(booking.cancelledAt()).contains(NOW);
        assertThat(booking.cancellationReason()).contains(REASON);
        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    // ── UC09: guide-initiated cancellation ───────────────────────────────────

    private TourBooking activeBookingForCancel() {
        return TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.ACTIVE);
    }

    /**
     * AC-02. A tour aborted mid-execution must be cancellable, and this is the one
     * transition where the guide may override a running tour — which is exactly why UC08
     * forbids the participant from doing the same.
     */
    @Test
    void cancel_byGuide_fromActive_transitionsToCancelled() {
        final TourBooking booking = activeBookingForCancel();

        booking.cancel(NOW, CancelledBy.GUIDE, REASON);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
        assertThat(booking.cancelledBy()).contains(CancelledBy.GUIDE);
    }

    /**
     * The same state the guide may cancel from is still closed to the participant. Asserted
     * alongside the GUIDE case so the asymmetry is pinned from both sides — widening the
     * guard for one caller must not widen it for the other.
     */
    @Test
    void cancel_byUser_fromActive_stillThrowsInvalidBookingStateException() {
        final TourBooking booking = activeBookingForCancel();

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.USER, REASON));
    }

    @Test
    void cancel_byGuide_fromConfirmed_recordsGuideAttribution() {
        final TourBooking booking = validBooking();
        booking.confirm(NOW);

        booking.cancel(NOW, CancelledBy.GUIDE, REASON);

        assertThat(booking.cancelledBy()).contains(CancelledBy.GUIDE);
        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
    }

    /**
     * A guide cancelling a tour must also cancel bookings nobody confirmed yet, or they sit
     * in REQUESTED forever waiting on a tour that will never run.
     */
    @Test
    void cancel_byGuide_fromRequested_transitionsToCancelled() {
        final TourBooking booking = validBooking();

        booking.cancel(NOW, CancelledBy.GUIDE, REASON);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CANCELLED);
        assertThat(booking.cancelledBy()).contains(CancelledBy.GUIDE);
    }

    @Test
    void cancel_byGuide_fromCompleted_throwsInvalidBookingStateException() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.COMPLETED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.GUIDE, REASON));
    }

    /**
     * AC-04, and the asymmetry with UC08 that matters most. UC12 calls this inside the
     * guide's transaction for every booking on the tour; if one already-cancelled booking
     * threw, the whole tour cancellation would roll back. A participant double-cancelling
     * still gets a 409 — see {@code cancel_whenAlreadyCancelled_doesNotOverwriteAttribution}.
     */
    @Test
    void cancel_byGuide_whenAlreadyCancelled_isIdempotentNoOp() {
        final TourBooking booking = validBooking();
        booking.cancel(NOW, CancelledBy.USER, REASON);
        booking.pullDomainEvents();

        booking.cancel(LATER, CancelledBy.GUIDE, OTHER_REASON);

        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    /**
     * The no-op must return <em>before</em> mutating, so a guide's bulk cancellation cannot
     * silently rewrite who originally cancelled a booking or why.
     */
    @Test
    void cancel_byGuide_whenAlreadyCancelled_doesNotOverwriteAttribution() {
        final TourBooking booking = validBooking();
        booking.cancel(NOW, CancelledBy.USER, REASON);
        booking.pullDomainEvents();

        booking.cancel(LATER, CancelledBy.GUIDE, OTHER_REASON);

        assertThat(booking.cancelledBy()).contains(CancelledBy.USER);
        assertThat(booking.cancelledAt()).contains(NOW);
        assertThat(booking.cancellationReason()).contains(REASON);
    }

    @Test
    void cancel_byGuide_publishesBookingCancelledByGuideEvent() {
        final TourBooking booking = validBooking();
        booking.confirm(NOW);
        booking.pullDomainEvents();

        booking.cancel(NOW, CancelledBy.GUIDE, REASON, GUIDE_TOUR_ID);

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).singleElement().isInstanceOf(BookingCancelledByGuide.class);
        final BookingCancelledByGuide event = (BookingCancelledByGuide) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.cancelledAt()).isEqualTo(NOW);
        assertThat(event.reason()).isEqualTo(REASON);
        assertThat(event.guideTourId()).isEqualTo(GUIDE_TOUR_ID);
    }

    /**
     * AC-05. The correlation id is event payload, not aggregate state (UC09 § 6), so it is
     * asserted on the event and deliberately nowhere else.
     */
    @Test
    void cancel_byGuide_carriesGuideTourIdOntoEvent_andDoesNotStoreIt() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents(); // drain TourBookingRequested

        booking.cancel(NOW, CancelledBy.GUIDE, REASON, "GT-999");

        final BookingCancelledByGuide event =
                (BookingCancelledByGuide) booking.pullDomainEvents().get(0);
        assertThat(event.guideTourId()).isEqualTo("GT-999");
        assertThat(TourBooking.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equals("guideTourId"));
    }

    @Test
    void cancel_byUser_viaFourArgOverload_rejectsAGuideTourId() {
        final TourBooking booking = validBooking();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> booking.cancel(NOW, CancelledBy.USER, REASON, GUIDE_TOUR_ID));
    }

    @Test
    void cancel_throwsNullPointerException_whenCancelledByIsNull() {
        final TourBooking booking = validBooking();

        assertThatNullPointerException()
                .isThrownBy(() -> booking.cancel(NOW, null, REASON));
    }

    // ── UC06: markActive ─────────────────────────────────────────────────────

    @Test
    void markActive_happyPath_transitionsToActive() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CONFIRMED);

        booking.markActive(NOW, "GT-001");

        assertThat(booking.status()).isEqualTo(TourBookingStatus.ACTIVE);
    }

    @Test
    void markActive_happyPath_recordsBookingActivatedEvent() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CONFIRMED);

        booking.markActive(NOW, "GT-001");

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(BookingActivated.class);
        final BookingActivated event = (BookingActivated) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.activatedAt()).isEqualTo(NOW);
        assertThat(event.guideTourId()).isEqualTo("GT-001");
    }

    @Test
    void markActive_idempotent_whenAlreadyActive_noEventEmitted() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.ACTIVE);

        booking.markActive(NOW, null);

        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    @Test
    void markActive_idempotent_whenAlreadyActive_statusRemainsActive() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.ACTIVE);

        booking.markActive(NOW, null);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.ACTIVE);
    }

    @Test
    void markActive_throwsInvalidBookingStateException_whenCancelled() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CANCELLED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.markActive(NOW, null));
    }

    @Test
    void markActive_throwsInvalidBookingStateException_whenCompleted() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.COMPLETED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.markActive(NOW, null));
    }

    // ── UC07: markCompleted ───────────────────────────────────────────────────

    private TourBooking activeBooking() {
        return TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.ACTIVE);
    }

    @Test
    void markCompleted_happyPath_transitionsToCompleted() {
        final TourBooking booking = activeBooking();

        booking.markCompleted(NOW, null);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.COMPLETED);
    }

    @Test
    void markCompleted_happyPath_recordsBookingCompletedEvent() {
        final TourBooking booking = activeBooking();

        booking.markCompleted(NOW, "GT-001");

        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).singleElement().isInstanceOf(BookingCompleted.class);
        final BookingCompleted event = (BookingCompleted) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.completedAt()).isEqualTo(NOW);
        assertThat(event.guideTourId()).isEqualTo("GT-001");
    }

    /**
     * The correlation id must survive the transition, exactly as `markActive` carries it
     * into {@code BookingActivated}. Without it the booking lifecycle is traceable back to
     * its guide-side cause on activation but not on completion — half a trail is not one.
     */
    @Test
    void markCompleted_happyPath_carriesGuideTourIdOntoEvent() {
        final TourBooking booking = activeBooking();

        booking.markCompleted(NOW, "GT-042");

        final BookingCompleted event = (BookingCompleted) booking.pullDomainEvents().get(0);
        assertThat(event.guideTourId()).isEqualTo("GT-042");
    }

    /**
     * {@code guideTourId} is a correlation id, not an invariant: a caller that has none
     * must still be able to complete a booking. Mirrors {@code markActive}, which accepts
     * null for the same reason.
     */
    @Test
    void markCompleted_acceptsNullGuideTourId() {
        final TourBooking booking = activeBooking();

        booking.markCompleted(NOW, null);

        final BookingCompleted event = (BookingCompleted) booking.pullDomainEvents().get(0);
        assertThat(event.guideTourId()).isNull();
        assertThat(booking.status()).isEqualTo(TourBookingStatus.COMPLETED);
    }

    @Test
    void markCompleted_idempotent_whenAlreadyCompleted_noEventEmitted() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.COMPLETED);

        booking.markCompleted(NOW, null);

        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    @Test
    void markCompleted_idempotent_whenAlreadyCompleted_statusRemainsCompleted() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.COMPLETED);

        booking.markCompleted(NOW, null);

        assertThat(booking.status()).isEqualTo(TourBookingStatus.COMPLETED);
    }

    @Test
    void markCompleted_throwsInvalidBookingStateException_whenRequested() {
        final TourBooking booking = validBooking();

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.markCompleted(NOW, null));
    }

    @Test
    void markCompleted_throwsInvalidBookingStateException_whenConfirmed() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CONFIRMED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.markCompleted(NOW, null));
    }

    @Test
    void markCompleted_throwsInvalidBookingStateException_whenCancelled() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CANCELLED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.markCompleted(NOW, null));
    }

    @Test
    void markCompleted_doesNotChangeStatus_whenStateInvalid() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CONFIRMED);

        try {
            booking.markCompleted(NOW, null);
        } catch (InvalidBookingStateException ignored) {
        }

        assertThat(booking.status()).isEqualTo(TourBookingStatus.CONFIRMED);
        assertThat(booking.pullDomainEvents()).isEmpty();
    }

    // ── UC04: changeParticipants ──────────────────────────────────────────────

    @Test
    void changeParticipants_increaseCount_updatesParticipantCountAndPublishesEvent() {
        final TourBooking booking = validBooking();
        booking.pullDomainEvents(); // drain TourBookingRequested
        final ParticipantCount newCount = new ParticipantCount(5);

        booking.changeParticipants(newCount, CAPACITY_10, NOW);

        assertThat(booking.participantCount()).isEqualTo(newCount);
        assertThat(booking.availableCapacity()).isEqualTo(CAPACITY_10);
        final List<DomainEvent> events = booking.pullDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(ParticipantsChanged.class);
        final ParticipantsChanged event = (ParticipantsChanged) events.get(0);
        assertThat(event.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(event.newParticipantCount()).isEqualTo(newCount);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void changeParticipants_decreaseCount_updatesParticipantCount() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, new ParticipantCount(8), CAPACITY_10, CONTACT,
                TourBookingStatus.REQUESTED);
        final ParticipantCount newCount = new ParticipantCount(3);

        booking.changeParticipants(newCount, CAPACITY_10, NOW);

        assertThat(booking.participantCount()).isEqualTo(newCount);
    }

    @Test
    void changeParticipants_fromCancelled_throwsInvalidBookingStateException() {
        final TourBooking booking = TourBooking.reconstitute(
                BOOKING_ID, TOUR_ID, TOUR_DATE, COUNT_2, CAPACITY_10, CONTACT,
                TourBookingStatus.CANCELLED);

        assertThatExceptionOfType(InvalidBookingStateException.class)
                .isThrownBy(() -> booking.changeParticipants(
                        new ParticipantCount(3), CAPACITY_10, NOW));
    }

    @Test
    void changeParticipants_exceedingCapacity_throwsCapacityExceededException() {
        final TourBooking booking = validBooking();
        final AvailableCapacity tightCapacity = new AvailableCapacity(2);

        assertThatExceptionOfType(CapacityExceededException.class)
                .isThrownBy(() -> booking.changeParticipants(
                        new ParticipantCount(5), tightCapacity, NOW));
    }
}
