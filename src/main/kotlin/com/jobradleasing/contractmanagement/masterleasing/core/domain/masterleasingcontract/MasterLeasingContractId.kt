package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import java.util.UUID

/**
 * Identity of a `MasterLeasingContract` (LRV), owned by this context.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class MasterLeasingContractId(
    val value: UUID,
)
