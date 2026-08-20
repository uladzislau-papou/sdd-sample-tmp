package com.dominikgaller.alpinebooking.shared.domain;

import java.util.Objects;

/**
 * Cross-context identity value object for an external tour definition.
 *
 * <p>Used by both the {@code booking} and {@code guide} bounded contexts
 * to reference the same tour catalog entry without coupling the contexts to each other.
 *
 * <p>Wraps a non-blank {@link String}.
 *
 * <p>SDD: See {@code documentation/adr/0003-separate-guide-bounded-context.adr.md}.
 */
public record TourId(String value) {

    public TourId {
        Objects.requireNonNull(value, "TourId value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TourId value must not be blank");
        }
    }
}
