package com.dominikgaller.alpinebooking.booking.core.domain;

import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingRequestException;

/**
 * Value object representing the number of participants for a booking.
 *
 * <p>Invariant: value must be >= 1. Violation throws {@link InvalidBookingRequestException}.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record ParticipantCount(int value) {

    public ParticipantCount {
        if (value < 1) {
            throw new InvalidBookingRequestException(
                    "Participant count must be >= 1, was: " + value);
        }
    }
}
