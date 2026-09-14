package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * *Kündigungsgrund* — the ground on which the Lessor terminated this contract.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class CancellationReason(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH = 400
    }

    init {
        if (value.isBlank()) {
            throw InvalidMasterLeasingContractException("cancellationReason must not be blank")
        }
        if (value.length > MAX_LENGTH) {
            throw InvalidMasterLeasingContractException(
                "cancellationReason must be at most $MAX_LENGTH characters: ${value.length}",
            )
        }
    }
}
