package com.dominikgaller.alpinebooking.booking.core.domain.exception;

/**
 * Thrown when a guide tour with the given ID cannot be found in the repository.
 *
 * <p>Maps to HTTP 404 Not Found at the REST boundary.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 8.
 */
public class GuideTourNotFoundException extends RuntimeException {

    public GuideTourNotFoundException(final String guideTourId) {
        super("Guide tour not found: " + guideTourId);
    }
}
