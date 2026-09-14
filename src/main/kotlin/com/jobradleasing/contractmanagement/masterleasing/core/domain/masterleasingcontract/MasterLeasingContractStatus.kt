package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

/**
 * Lifecycle states of a `MasterLeasingContract`.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 3.
 */
enum class MasterLeasingContractStatus {
    DRAFT,
    ACTIVE,
    CANCELLED,
    ENDED,
}
