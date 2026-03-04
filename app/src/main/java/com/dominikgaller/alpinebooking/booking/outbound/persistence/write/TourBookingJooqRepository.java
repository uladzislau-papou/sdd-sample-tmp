package com.dominikgaller.alpinebooking.booking.outbound.persistence.write;

import com.dominikgaller.alpinebooking.booking.core.domain.BookingId;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBooking;
import com.dominikgaller.alpinebooking.booking.core.outport.TourBookingRepository;
import com.dominikgaller.alpinebooking.jooq.tables.records.TourBookingRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.dominikgaller.alpinebooking.jooq.Tables.TOUR_BOOKING;

/**
 * jOOQ-backed implementation of {@link TourBookingRepository}.
 *
 * <p>Supports insert ({@link #save}), point-lookup ({@link #findById}),
 * and status update ({@link #update}) on the {@code tour_booking} table.
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

    @Override
    public Optional<TourBooking> findById(final BookingId bookingId) {
        final TourBookingRecord record = dsl
                .selectFrom(TOUR_BOOKING)
                .where(TOUR_BOOKING.ID.eq(bookingId.value().toString()))
                .fetchOne();
        return Optional.ofNullable(record).map(mapper::toDomain);
    }

    @Override
    public void update(final TourBooking booking) {
        final int rowsUpdated = dsl
                .update(TOUR_BOOKING)
                .set(TOUR_BOOKING.STATUS, booking.status().name())
                .where(TOUR_BOOKING.ID.eq(booking.bookingId().value().toString()))
                .execute();
        if (rowsUpdated != 1) {
            throw new IllegalStateException(
                    "Expected to update exactly 1 row for booking " +
                    booking.bookingId().value() + " but updated " + rowsUpdated);
        }
    }
}
