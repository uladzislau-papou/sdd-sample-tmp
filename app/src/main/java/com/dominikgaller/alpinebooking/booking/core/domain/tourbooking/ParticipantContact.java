package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import java.util.Objects;

/**
 * Value object representing the contact person for a booking.
 *
 * <p>Both {@code name} and {@code email} must be non-blank.
 * No deep email format validation is performed; non-blank is sufficient.
 *
 * <p>SDD: See {@code documentation/domain/aggregate-tour-booking.spec.md}, section 2a.
 */
public record ParticipantContact(String name, String email) {

    public ParticipantContact {
        Objects.requireNonNull(name, "Contact name must not be null");
        Objects.requireNonNull(email, "Contact email must not be null");
        if (name.isBlank()) {
            throw new InvalidBookingRequestException("Contact name must not be blank");
        }
        if (email.isBlank()) {
            throw new InvalidBookingRequestException("Contact email must not be blank");
        }
    }
}
