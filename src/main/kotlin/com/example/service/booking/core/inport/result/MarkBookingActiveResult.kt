package com.example.service.booking.core.inport.result

/**
 * Output of the MarkBookingActive use case (UC06).
 *
 * SDD: see `documentation/use-cases/uc06-mark-booking-active.spec.md` § 3.
 */
data class MarkBookingActiveResult(
    val status: String,
)
