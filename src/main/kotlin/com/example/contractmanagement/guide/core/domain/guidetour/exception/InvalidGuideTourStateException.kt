package com.example.contractmanagement.guide.core.domain.guidetour.exception

import com.example.contractmanagement.guide.core.domain.guidetour.GuideTourStatus

/** Thrown when a transition is attempted from a state that does not permit it. Maps to 409. */
class InvalidGuideTourStateException(
    currentStatus: GuideTourStatus,
) : RuntimeException("Invalid state transition from state: $currentStatus")
