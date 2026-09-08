package com.example.contractmanagement.mlc.core.inport.usecase

import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.inport.result.CreateMasterLeasingContractResult

/**
 * Inbound port for UC07 — CreateMasterLeasingContract.
 *
 * The only entry point for bringing a master leasing contract into existence from outside the
 * core. Framework-free: no Spring, no Jakarta annotations.
 *
 * **Described from the domain's needs, not from a caller's message.** The eventual trigger is
 * employer onboarding (AGO), whose message format is documented nowhere and is deliberately
 * not guessed at (`project.definition.md` non-goals). A GraphQL mutation is this port's first
 * adapter (`adr/0020`), not its definition — which is the point of the port.
 *
 * The transaction boundary belongs to the driver. A caller MUST NOT wrap this in its own
 * transaction: `adr/0022` has the outbox row written inside this method's transaction, so an
 * adapter that owned the boundary would own a domain guarantee.
 *
 * SDD: see `documentation/ports/create-master-leasing-contract.inport.spec.md`.
 */
interface CreateMasterLeasingContractUseCase {
    fun create(command: CreateMasterLeasingContractCommand): CreateMasterLeasingContractResult
}
