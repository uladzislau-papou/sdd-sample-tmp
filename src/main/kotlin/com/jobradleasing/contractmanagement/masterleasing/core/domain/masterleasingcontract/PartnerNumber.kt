package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException

/**
 * The *Partnernummer*, the Odoo <-> Radar join key.
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
data class PartnerNumber(
    val value: String,
) {
    init {
        if (value.isBlank()) {
            throw InvalidMasterLeasingContractException("partnerNumber must not be blank")
        }
    }
}
