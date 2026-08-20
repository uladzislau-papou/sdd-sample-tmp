package com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception;

/**
 * Thrown by {@link AvailabilityChecker} when the availability source cannot be reached
 * or returns an invalid response.
 *
 * <p>Maps to HTTP 502 Bad Gateway at the REST boundary.
 *
 * <p><b>Why it lives here.</b> This is an infrastructure failure, not a domain invariant
 * violation, so this package is not an obvious home. It is nonetheless the only
 * ontology-conformant one: {@code architecture.definition.md} section 4.2 lets a
 * {@code *UseCase} interface reference {@code core.domain.<aggregate>.exception} and
 * nothing else, and section 4.5 lets {@code inbound.rest} reference domain exceptions.
 * Both need this type — the use cases declare it, the exception handler maps it — so this
 * is the single package both may see. Leaving it in {@code core.outport} forced
 * {@code BookingExceptionHandler} to import from {@code core.outport}, violating
 * section 6 rule 3.
 *
 * <p>The tidier alternative — a dedicated {@code core.inport.exception} package for
 * application-layer failures — would change the package ontology and therefore needs an
 * ADR ({@code sdd.playbook.md} section 6 item 8). Recorded as a future consideration
 * rather than done silently.
 *
 * <p>SDD: See {@code documentation/ports/availability-checker.outport.spec.md}, section 2.1.
 */
public class AvailabilityUnavailableException extends RuntimeException {

    public AvailabilityUnavailableException(final String message) {
        super(message);
    }

    public AvailabilityUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
