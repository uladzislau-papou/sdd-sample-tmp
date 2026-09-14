package com.jobradleasing.contractmanagement.masterleasing.core.outport

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContract
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContractId

/**
 * Persistence boundary for the `MasterLeasingContract` aggregate, write side.
 *
 * SDD: See `documentation/ports/master-leasing-contract-repository.outport.spec.md`.
 */
interface MasterLeasingContractRepository {
    /**
     * Persists a new aggregate atomically, including its `MlcConfiguration`.
     *
     * @param contract a contract not yet persisted; no row may already exist for its id
     */
    fun save(contract: MasterLeasingContract)

    /**
     * Loads one aggregate by identity, fully reconstituted, with its current
     * configuration. The returned aggregate is detached.
     *
     * @return the aggregate, or null if no row matches — absence is not an error
     */
    fun findById(contractId: MasterLeasingContractId): MasterLeasingContract?

    /**
     * Persists state changes to an existing aggregate. Every mutable property is
     * written; a row must already exist for [contract]'s id.
     */
    fun update(contract: MasterLeasingContract)
}
