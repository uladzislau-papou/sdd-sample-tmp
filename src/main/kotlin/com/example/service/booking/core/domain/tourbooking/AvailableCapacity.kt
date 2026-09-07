package com.example.service.booking.core.domain.tourbooking

/**
 * Open spots on a tour, as reported by the availability system at a point in time.
 *
 * A snapshot, not a live figure. It is stored on the aggregate so that a later decision
 * can be judged against the capacity that was actually observed when it was made.
 *
 * Uses [IllegalArgumentException] deliberately: this value never arrives from a command,
 * only from the `AvailabilityChecker` outport or from reconstitution, so a negative value
 * is a programmer error rather than bad input (`coding-style.definition.md` § 6.2).
 *
 * SDD: see `documentation/domain/aggregate-tour-booking.spec.md`.
 */
data class AvailableCapacity(
    val value: Int,
) {
    init {
        require(value >= 0) { "AvailableCapacity must be >= 0, was: $value" }
    }
}
