package com.example.contractmanagement.guide.core.domain.guidetour

/**
 * Lifecycle of a [GuideTour].
 *
 * Only the states this example can actually reach are declared. The original had
 * `FINISHED` and `CANCELLED` as well, for the complete-tour and cancel-tour use cases that
 * are not part of the template's example. They are omitted rather than kept as documentation:
 * an enum value no transition produces forces every exhaustive `when` to carry a branch
 * that can never run, and `coding-style.definition.md` § 2.3 forbids the `else` that would
 * otherwise hide it.
 *
 * A service adding those use cases adds the states with them.
 *
 * SDD: see `documentation/domain/aggregate-guide-tour.spec.md`.
 */
enum class GuideTourStatus {
    SCHEDULED,
    RUNNING,
}
