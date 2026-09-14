package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception

/**
 * Thrown when a `MasterLeasingContract` creation or value-object invariant is
 * violated.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2.
 */
class InvalidMasterLeasingContractException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
