package com.example.service.guide.core.inport.result

/**
 * Output of the StartTour use case (UC05).
 *
 * SDD: see `documentation/ports/start-tour.inport.spec.md` § 2.2.
 */
data class StartTourResult(
    val status: String,
)
