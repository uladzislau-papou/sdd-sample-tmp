package com.example.service.booking.outbound.integration

import com.example.service.booking.core.domain.tourbooking.AvailableCapacity
import com.example.service.booking.core.domain.tourbooking.TourDate
import com.example.service.booking.core.outport.AvailabilityChecker
import com.example.service.shared.domain.TourId

/**
 * Stub adapter for [AvailabilityChecker]: always reports effectively unlimited capacity.
 *
 * The example has no real availability system to call, and inventing one would add an
 * integration nobody specified. What the stub still demonstrates is the shape that matters
 * — the core depends on the port, the port has an adapter in `outbound.integration`, and
 * swapping in a real HTTP client changes nothing inside `core`.
 *
 * A service built from this template replaces this class. Until then, note what it means:
 * the capacity invariant in `TourBooking.request` is exercised by tests but never by this
 * adapter, so a capacity conflict cannot occur at runtime here.
 *
 * SDD: see `documentation/ports/availability-checker.outport.spec.md`.
 */
class StubAvailabilityChecker : AvailabilityChecker {
    override fun checkAvailability(
        tourId: TourId,
        tourDate: TourDate,
    ): AvailableCapacity = AvailableCapacity(Int.MAX_VALUE)
}
