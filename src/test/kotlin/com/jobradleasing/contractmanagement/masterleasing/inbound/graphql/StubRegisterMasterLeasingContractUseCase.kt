package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql

import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.RegisterMasterLeasingContractCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.result.RegisterMasterLeasingContractResult
import com.jobradleasing.contractmanagement.masterleasing.core.inport.usecase.RegisterMasterLeasingContractUseCase

/** Test double for the GraphQL slice: no mocking framework is available (test.definition.md § 1.1). */
class StubRegisterMasterLeasingContractUseCase : RegisterMasterLeasingContractUseCase {
    var result: RegisterMasterLeasingContractResult? = null
    var exceptionToThrow: RuntimeException? = null
    var receivedCommand: RegisterMasterLeasingContractCommand? = null
        private set

    override fun register(command: RegisterMasterLeasingContractCommand): RegisterMasterLeasingContractResult {
        receivedCommand = command
        exceptionToThrow?.let { throw it }
        return result ?: error("StubRegisterMasterLeasingContractUseCase.result was not configured")
    }
}
