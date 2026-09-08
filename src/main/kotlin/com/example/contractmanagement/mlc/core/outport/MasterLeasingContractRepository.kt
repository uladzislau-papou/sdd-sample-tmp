package com.example.contractmanagement.mlc.core.outport

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContract
import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.MasterLeasingContractId

/**
 * Outbound port for persisting the `MasterLeasingContract` aggregate.
 *
 * Each operation must be atomic, and [save] must write the contract **and** its current
 * configuration — they are one aggregate, and a save that persisted the contract while
 * dropping its terms would leave a row that no invariant describes.
 *
 * **No `update` yet, and no `findByEmployerId`.** The port carries what UC07 needs and nothing
 * more. `update` arrives with the first use case that changes a contract; a lookup by employer
 * would only exist to enforce one-active-contract-per-employer, which **PD-05** decided against
 * — and `uc07` § 3 records that if PD-05 is overturned the rule needs a database constraint
 * rather than a port method, because `project.definition.md`'s last-write-wins non-goal means
 * two concurrent creations would both read "no existing contract" and both succeed.
 *
 * [findById] exists for the persistence roundtrip test rather than for a use case: there is no
 * read side (`project.definition.md`), so without it nothing could verify that what was
 * written can be read back.
 *
 * SDD: see `documentation/ports/master-leasing-contract-repository.outport.spec.md`.
 */
interface MasterLeasingContractRepository {
    /** Persists the contract together with its current configuration. */
    fun save(contract: MasterLeasingContract)

    /** Returns the aggregate, or null when no contract has that identity. */
    fun findById(id: MasterLeasingContractId): MasterLeasingContract?
}
