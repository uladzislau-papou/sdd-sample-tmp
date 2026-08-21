package com.dominikgaller.alpinebooking.guide.core.domain.guidetour;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidCancellationReasonException;

import java.util.Objects;

/**
 * Value object carrying why a guide called a tour off (UC12).
 *
 * <p>Must be non-blank and at most {@value #MAX_LENGTH} characters. An <em>absent</em>
 * reason is represented by the absence of this object — cancelling without giving one is
 * permitted, cancelling with an empty one is not.
 *
 * <p>This context needs its own type, rather than validating in the driver, because it
 * <b>stores</b> the value: {@link GuideTour} holds it and {@code guide_tour} has a sized
 * column for it. That makes the length a {@code guide} invariant, and an invariant enforced
 * in an application service is not enforced at all — {@code GuideTour.cancel} could
 * otherwise build a valid-by-construction aggregate that no {@code UPDATE} would accept
 * ({@code modelling.definition.md} § Always-Valid).
 *
 * <p>Deliberately <b>not</b> shared with {@code booking}'s value object of the same name:
 * a domain type must not cross a context boundary
 * ({@code architecture.definition.md} § 11 rule 3). Only the {@code String} crosses the
 * inport. The two ceilings are kept equal by
 * {@code CancellationReasonTest.ceilingMatchesTheBookingSideCeiling} — if they drifted,
 * {@code guide} would accept a reason {@code booking} then rejected mid-transaction,
 * turning a 400 into a 502.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md}, section 2.
 */
public record CancellationReason(String value) {

    /** Matches {@code booking}'s ceiling, and the {@code VARCHAR(400)} column width. */
    public static final int MAX_LENGTH = 400;

    public CancellationReason {
        Objects.requireNonNull(value, "Cancellation reason must not be null");
        if (value.isBlank()) {
            throw new InvalidCancellationReasonException(
                    "Cancellation reason must not be blank");
        }
        // Checked on the raw value, before trimming, so trailing whitespace cannot carry
        // the text past the ceiling and then vanish.
        if (value.length() > MAX_LENGTH) {
            throw new InvalidCancellationReasonException(
                    "Cancellation reason must be at most " + MAX_LENGTH
                            + " characters, but was " + value.length());
        }
    }
}
