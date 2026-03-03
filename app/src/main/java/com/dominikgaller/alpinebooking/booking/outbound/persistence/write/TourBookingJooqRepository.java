package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.jooq.tables.records.TourBookingRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.dominikgaller.alpinebooking.jooq.Tables.TOUR_BOOKING;

/**
 * jOOQ-backed implementation of {@link TourBookingRepository}.
 *
 * <p>Persists a {@link TourBooking} aggregate as a single {@code INSERT} into the
 * {@code tour_booking} table. No update path is needed for UC01.
 *
 * <p>SDD: See {@code documentation/ports/tour-booking-repository.outport.spec.md}.
 */
@Repository
public class TourBookingJooqRepository implements TourBookingRepository {

    private final DSLContext dsl;
    private final TourBookingMapper mapper = new TourBookingMapper();

    public TourBookingJooqRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(final TourBooking booking) {
        final TourBookingRecord record = mapper.toRecord(booking);
        dsl.insertInto(TOUR_BOOKING).set(record).execute();
    }
}
