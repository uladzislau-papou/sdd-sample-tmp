package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

/**
 * Value object representing the number of open spots for a tour/date at booking time.
 *
 * <p>Stored as a snapshot in the aggregate. Invariant: value must be >= 0.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record AvailableCapacity(int value) {

    public AvailableCapacity {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "AvailableCapacity must be >= 0, was: " + value);
        }
    }
}
