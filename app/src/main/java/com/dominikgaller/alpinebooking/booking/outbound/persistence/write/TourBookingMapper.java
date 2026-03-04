package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.AvailableCapacity;
import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantContact;
import com.dominikgaller.alpinebooking.booking.core.domain.ParticipantCount;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.domain.TourDate;
import com.dominikgaller.alpinebooking.booking.core.domain.TourId;
import com.dominikgaller.alpinebooking.jooq.tables.records.TourBookingRecord;

import java.util.UUID;

/**
 * Pure mapping utility: converts between the {@link TourBooking} aggregate and
 * the jOOQ {@link TourBookingRecord}.
 *
 * <p>No Spring dependency. No IO. All field extraction delegates to the aggregate's
 * public getters ({@link #toRecord}) or to {@link TourBooking#reconstitute}
 * ({@link #toDomain}).
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}
 *          and {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md}.
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
                TourBookingStatus.valueOf(record.getStatus())
        );
    }
}
