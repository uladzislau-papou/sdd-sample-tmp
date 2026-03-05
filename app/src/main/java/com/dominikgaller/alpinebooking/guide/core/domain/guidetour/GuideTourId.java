package com.dominikgaller.alpinebooking.guide.core.domain.guidetour;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for a {@link GuideTour} aggregate.
 *
 * <p>Wraps a {@link UUID}. Equality is by value (record semantics).
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
public record GuideTourId(UUID value) {

    public GuideTourId {
        Objects.requireNonNull(value, "GuideTourId value must not be null");
    }

    public static GuideTourId generate() {
        return new GuideTourId(UUID.randomUUID());
    }
}
