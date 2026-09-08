package com.example.contractmanagement.shared.domain

/**
 * Cross-context identity value object for an external tour definition.
 *
 * Used by both the `booking` and `guide` bounded contexts to reference the same tour
 * catalog entry without coupling the contexts to each other.
 *
 * SDD: see `documentation/adr/0003-separate-guide-bounded-context.adr.md`.
 */
data class TourId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "TourId value must not be blank" }
    }
}
