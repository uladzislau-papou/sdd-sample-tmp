package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.jooq.tables.records.TourBookingRecord;

/**
 * Pure mapping utility: converts a {@link TourBooking} aggregate to a jOOQ {@link TourBookingRecord}.
 *
 * <p>No Spring dependency. No IO. All field extraction delegates to the aggregate's public getters.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md}.
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
}
