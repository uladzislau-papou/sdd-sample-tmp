package com.example.contractmanagement.booking.core.outport

import com.example.contractmanagement.booking.core.domain.tourbooking.AvailableCapacity
import com.example.contractmanagement.booking.core.domain.tourbooking.TourDate
import com.example.contractmanagement.shared.domain.TourId

/**
 * Outbound port for querying available capacity for a tour on a date.
 *
 * Supplies the data the domain needs to enforce the capacity invariant. It does **not**
 * enforce it — that belongs to `TourBooking`. A port that decided this would move a
 * domain rule into infrastructure, where no domain test can reach it.
 *
 * SDD: see `documentation/ports/availability-checker.outport.spec.md`.
 */
interface AvailabilityChecker {
    /**
     * Returns the available capacity for [tourId] on [tourDate].
     *
     * The value is a snapshot; no capacity is reserved by this call.
     *
     * @throws com.example.contractmanagement.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException
     *   if the availability source is unreachable
     */
    fun checkAvailability(
        tourId: TourId,
        tourDate: TourDate,
    ): AvailableCapacity
}
