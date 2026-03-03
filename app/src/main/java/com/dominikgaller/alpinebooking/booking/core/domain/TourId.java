package com.dominikgaller.alpinebooking.booking.core.domain;

import java.util.Objects;

/**
 * Identity value object for an external tour definition.
 *
 * <p>Wraps a non-blank {@link String}.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record TourId(String value) {

    public TourId {
        Objects.requireNonNull(value, "TourId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TourId value must not be blank");
        }
    }
}
