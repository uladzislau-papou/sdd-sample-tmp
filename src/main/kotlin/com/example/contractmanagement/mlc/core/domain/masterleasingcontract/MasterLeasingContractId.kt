package com.example.contractmanagement.mlc.core.domain.masterleasingcontract

import java.util.UUID

/**
 * Identity of a [MasterLeasingContract].
 *
 * A **category 1** identity in `adr/0005`'s terms — owned by this context, so a Value Object
 * defined inside it and never in `shared`.
 *
 * SDD: see `documentation/domain/aggregate-master-leasing-contract.spec.md`.
 */
data class MasterLeasingContractId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): MasterLeasingContractId = MasterLeasingContractId(value)

        fun generate(): MasterLeasingContractId = MasterLeasingContractId(UUID.randomUUID())
    }
}
