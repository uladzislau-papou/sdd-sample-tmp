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
import com.dominikgaller.alpinebooking.jooq.tables.records.TourBookingRecord;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Pure mapping utility: converts between the {@link TourBooking} aggregate and
 * the jOOQ {@link TourBookingRecord}.
 *
 * <p>No Spring dependency. No IO. All field extraction delegates to the aggregate's
 * public getters ({@link #toRecord}) or to {@link TourBooking#reconstitute}
 * ({@link #toDomain}).
 *
 * <p>{@code TIMESTAMP} columns are stored as UTC {@link LocalDateTime} and converted
 * to/from {@link java.time.Instant} at {@link ZoneOffset#UTC}, matching the guide side.
 * Storing local time would make the value depend on the server's zone.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md},
 *          {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}
 *          and {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}.
 */
class TourBookingMapper {

    TourBookingRecord toRecord(final TourBooking booking) {
        final TourBookingRecord record = new TourBookingRecord();
        record.setId(booking.bookingId().value().toString());
        record.setTourId(booking.tourId().value());
        record.setTourDate(booking.tourDate().value());
        record.setParticipantCount(booking.participantCount().value());
        record.setAvailableCapacity(booking.availableCapacity().value());
        record.setContactName(booking.contact().name());
        record.setContactEmail(booking.contact().email());
        record.setStatus(booking.status().name());
        // UC08 attribution. Nulls are written as nulls: a booking that was never cancelled
        // must be distinguishable from one cancelled without a reason.
        record.setCancelledAt(booking.cancelledAt()
                .map(instant -> LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
                .orElse(null));
        record.setCancelledBy(booking.cancelledBy().map(Enum::name).orElse(null));
        record.setCancellationReason(booking.cancellationReason()
                .map(CancellationReason::value)
                .orElse(null));
        return record;
    }

    TourBooking toDomain(final TourBookingRecord record) {
        return TourBooking.reconstitute(
                new BookingId(UUID.fromString(record.getId())),
                new TourId(record.getTourId()),
                new TourDate(record.getTourDate()),
                new ParticipantCount(record.getParticipantCount()),
                new AvailableCapacity(record.getAvailableCapacity()),
                new ParticipantContact(record.getContactName(), record.getContactEmail()),
                TourBookingStatus.valueOf(record.getStatus()),
                record.getCancelledAt() == null
                        ? null
                        : record.getCancelledAt().toInstant(ZoneOffset.UTC),
                record.getCancelledBy() == null
                        ? null
                        : CancelledBy.valueOf(record.getCancelledBy()),
                record.getCancellationReason() == null
                        ? null
                        : new CancellationReason(record.getCancellationReason())
        );
    }
}
