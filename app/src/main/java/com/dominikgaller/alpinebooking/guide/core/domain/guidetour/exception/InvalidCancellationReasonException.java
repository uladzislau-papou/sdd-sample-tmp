package com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception;

/**
 * A cancellation reason was blank or longer than the permitted length (UC12).
 *
 * <p>A domain exception rather than {@code IllegalArgumentException}, because this is a
 * business rule about a value this context stores, not a programmer error
 * ({@code coding-style.definition.md} § 6.2 clause A: a value object constructed from a
 * command must throw a domain exception). Maps to HTTP 400.
 *
 * <p>SDD: See {@code documentation/use-cases/uc12-cancel-tour-by-guide.spec.md} section 3.
 */
public class InvalidCancellationReasonException extends RuntimeException {

    public InvalidCancellationReasonException(final String message) {
        super(message);
    }
}
