package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.event

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContractId
import com.jobradleasing.contractmanagement.shared.domain.EmployerId
import com.jobradleasing.contractmanagement.shared.domain.LessorId
import com.jobradleasing.contractmanagement.shared.domain.event.DomainEvent
import java.time.Instant

/**
 * A `MasterLeasingContract` was registered in `DRAFT`. Context-local — no other
 * context consumes it.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 5.
 */
data class MasterLeasingContractRegistered(
    val contractId: MasterLeasingContractId,
    val employerId: EmployerId,
    val lessorId: LessorId,
    val occurredAt: Instant,
) : DomainEvent
