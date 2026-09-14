package com.jobradleasing.contractmanagement.masterleasing.inbound.driver

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContract
import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.MasterLeasingContractId
import com.jobradleasing.contractmanagement.masterleasing.core.outport.MasterLeasingContractRepository

/** Test double recording every saved/updated aggregate, for driver-test assertions. */
class RecordingMasterLeasingContractRepository : MasterLeasingContractRepository {
    val saved: MutableList<MasterLeasingContract> = mutableListOf()
    val updated: MutableList<MasterLeasingContract> = mutableListOf()
    private val byId: MutableMap<MasterLeasingContractId, MasterLeasingContract> = mutableMapOf()

    override fun save(contract: MasterLeasingContract) {
        saved.add(contract)
        byId[contract.contractId] = contract
    }

    override fun findById(contractId: MasterLeasingContractId): MasterLeasingContract? = byId[contractId]

    override fun update(contract: MasterLeasingContract) {
        updated.add(contract)
        byId[contract.contractId] = contract
    }
}
