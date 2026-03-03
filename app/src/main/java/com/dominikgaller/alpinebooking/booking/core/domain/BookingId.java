package com.dominikgaller.alpinebooking.booking.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for a TourBooking aggregate.
 *
 * <p>Wraps a {@link UUID}. Equality is by value (record semantics).
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "BookingId value must not be null");
    }

    public static BookingId of(final UUID value) {
        return new BookingId(value);
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }
}
