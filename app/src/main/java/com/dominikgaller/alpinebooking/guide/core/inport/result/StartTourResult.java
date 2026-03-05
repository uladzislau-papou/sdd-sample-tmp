package com.dominikgaller.alpinebooking.guide.core.inport.result;

/**
 * Output data carrier for the UC05 – StartTour use case.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}, section 3.
 */
public record StartTourResult(String status) {
}
