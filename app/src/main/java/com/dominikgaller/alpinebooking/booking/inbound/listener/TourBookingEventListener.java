package com.dominikgaller.alpinebooking.booking.inbound.listener;

import com.dominikgaller.alpinebooking.booking.core.domain.event.TourBookingRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener that reacts to {@link TourBookingRequested} after the transaction commits.
 *
 * <p>Triggered by {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, ensuring the
 * side-effect (logging here; real notification in future iterations) only runs when the
 * booking has been safely persisted. See ADR 0002.
 *
 * <p>SDD: See {@code documentation/adr/0002-domain-event-publication.adr.md}.
 */
@Component
public class TourBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(TourBookingEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTourBookingRequested(final TourBookingRequested event) {
        log.info("TourBookingRequested received after commit: bookingId={}, tourId={}, date={}, participantCount={}, occurredAt={}",
                event.bookingId().value(),
                event.tourId().value(),
                event.tourDate().value(),
                event.participantCount().value(),
                event.occurredAt());
    }
}
