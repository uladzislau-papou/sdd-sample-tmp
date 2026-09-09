package com.example.contractmanagement.contract.core.domain.master.exception

/**
 * Raised when a `Master`'s own data violates an invariant it owns — I-01 (`MasterName`) or
 * I-02 (`CustomerNumber`).
 *
 * A domain exception rather than [IllegalArgumentException], because both value objects are
 * constructed from a command — data that entered through an inbound port. The domain may not
 * assume an adapter validated first: the same use case may later be driven by a consumer or a
 * scheduler with no validation in front of it, and the value object is then the only guard
 * (`coding-style.definition.md` § 6.2).
 *
 * Extends nothing framework-bound, so the core stays free of Spring and Jakarta
 * (`aggregate-master.spec.md` § 6).
 *
 * SDD: see `documentation/domain/aggregate-master.spec.md` § 2 and § 6.
 */
class InvalidMasterException(
    message: String,
) : RuntimeException(message)
