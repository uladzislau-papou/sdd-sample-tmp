package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;

import java.util.Objects;

/**
 * Value object carrying the free-text reason a booking was cancelled (UC08).
 *
 * <p>Must be non-blank and at most {@value #MAX_LENGTH} characters. An <em>absent</em>
 * reason is represented by the absence of this object — cancelling without giving a
 * reason is permitted, cancelling with an empty one is not.
 *
 * <p>The ceiling is enforced here rather than by a Bean Validation annotation on the
 * request DTO so that it holds for every caller, not only the REST adapter: UC09 reaches
 * the same aggregate method from the {@code guide} side with no DTO in the path.
 *
 * <p>SDD: See {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md}, section 2.
 */
public record CancellationReason(String value) {

    /**
     * Bounded so {@code cancellation_reason} can be a sized {@code VARCHAR} rather than an
     * unbounded {@code TEXT}, and small enough to stay human-readable free text.
     */
    public static final int MAX_LENGTH = 400;

    public CancellationReason {
        Objects.requireNonNull(value, "Cancellation reason must not be null");
        if (value.isBlank()) {
            throw new InvalidBookingRequestException("Cancellation reason must not be blank");
        }
        // Length is checked on the raw value, before any trimming, so trailing whitespace
        // cannot carry the text past the ceiling and then vanish.
        if (value.length() > MAX_LENGTH) {
            throw new InvalidBookingRequestException(
                    "Cancellation reason must be at most " + MAX_LENGTH
                            + " characters, but was " + value.length());
        }
    }
}
