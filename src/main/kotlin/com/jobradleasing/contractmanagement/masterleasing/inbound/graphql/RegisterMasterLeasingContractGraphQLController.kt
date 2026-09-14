package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql

import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.MlcConfigurationCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.command.RegisterMasterLeasingContractCommand
import com.jobradleasing.contractmanagement.masterleasing.core.inport.usecase.RegisterMasterLeasingContractUseCase
import com.jobradleasing.contractmanagement.masterleasing.inbound.graphql.input.RegisterMasterLeasingContractInput
import com.jobradleasing.contractmanagement.masterleasing.inbound.graphql.payload.RegisterMasterLeasingContractPayload
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import java.math.BigDecimal

/**
 * Resolver for UC01 — RegisterMasterLeasingContract. Maps input to command,
 * delegates to the inport, maps result to payload — nothing else
 * (architecture.definition.md § 4.5).
 *
 * SDD: See `documentation/use-cases/uc01-register-master-leasing-contract.spec.md` § 9.
 */
@Controller
class RegisterMasterLeasingContractGraphQLController(
    private val registerMasterLeasingContractUseCase: RegisterMasterLeasingContractUseCase,
) {
    @MutationMapping
    fun registerMasterLeasingContract(
        @Argument input: RegisterMasterLeasingContractInput,
    ): RegisterMasterLeasingContractPayload {
        val command =
            RegisterMasterLeasingContractCommand(
                employerId = input.employerId,
                lessorId = input.lessorId,
                partnerNumber = input.partnerNumber,
                parentMasterLeasingContractId = input.parentMasterLeasingContractId,
                configuration =
                    MlcConfigurationCommand(
                        contractType = input.configuration.contractType,
                        salesChannel = input.configuration.salesChannel,
                        inheritanceMode = input.configuration.inheritanceMode,
                        currency = input.configuration.currency,
                        creditLimitAmount = BigDecimal(input.configuration.creditLimitAmount),
                        priceRangeMin = BigDecimal(input.configuration.priceRangeMin),
                        priceRangeMax = BigDecimal(input.configuration.priceRangeMax),
                        eligibleEmployees = input.configuration.eligibleEmployees,
                        jointLiability = input.configuration.jointLiability,
                        returnQuotaPercentage = BigDecimal(input.configuration.returnQuotaPercentage),
                        noticePeriodMonths = input.configuration.noticePeriodMonths,
                    ),
            )

        val result = registerMasterLeasingContractUseCase.register(command)

        return RegisterMasterLeasingContractPayload(
            masterLeasingContractId = result.masterLeasingContractId,
            status = result.status,
            configurationVersion = result.configurationVersion,
        )
    }
}
