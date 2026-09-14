package com.jobradleasing.contractmanagement.masterleasing.core.inport.usecase

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.RegisterMasterLeasingContractCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.result.RegisterMasterLeasingContractResult

/**
 * The inbound application boundary for UC01 — RegisterMasterLeasingContract.
 *
 * SDD: See `documentation/ports/register-master-leasing-contract.inport.spec.md`.
 */
interface RegisterMasterLeasingContractUseCase {
    /**
     * Registers a new `MasterLeasingContract` in `DRAFT`, at configuration version 1.
     *
     * @throws InvalidMasterLeasingContractException when any creation or
     *   value-object invariant is violated (I-01, I-02, I-03, I-09 to I-13), or
     *   the currency is not a valid ISO-4217 code
     * @throws IllegalArgumentException when a `Money`/`Percentage` invariant is
     *   violated, or `parentMasterLeasingContractId` is malformed
     */
    fun register(command: RegisterMasterLeasingContractCommand): RegisterMasterLeasingContractResult
}
