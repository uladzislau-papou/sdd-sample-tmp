package com.example.contractmanagement.guide.core.inport.command

import java.time.Instant

/**
 * Input for the StartTour use case (UC05).
 *
 * @property startedAt the actual start time. Null means the caller has no authoritative
 *   time and the driver reads `ClockPort` instead. A guide pressing "start" now sends
 *   nothing; a client replaying a recorded start sends the recorded instant.
 *
 * SDD: see `documentation/ports/start-tour.inport.spec.md` § 2.1.
 */
data class StartTourCommand(
    val guideTourId: String,
    val startedAt: Instant?,
)
