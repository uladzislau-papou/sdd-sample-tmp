package com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract

/**
 * Whether terms inherited by a lease stay linked to this contract or are
 * copied once. Only `COPIED_ONCE` is implemented (`documentation/notes.md`).
 *
 * SDD: See `documentation/domain/aggregate-master-leasing-contract.spec.md` § 2a.
 */
enum class InheritanceMode {
    LIVE_LINKED,
    COPIED_ONCE,
}
