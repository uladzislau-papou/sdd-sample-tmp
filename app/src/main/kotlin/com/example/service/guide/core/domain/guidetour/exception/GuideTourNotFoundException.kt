package com.example.service.guide.core.domain.guidetour.exception

/** Thrown when no guide tour exists for a given identity. Maps to 404. */
class GuideTourNotFoundException(
    guideTourId: String,
) : RuntimeException("Guide tour not found: $guideTourId")
