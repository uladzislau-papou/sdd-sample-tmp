package com.example.contractmanagement.mlc.inbound.graphql

import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.inport.usecase.CreateMasterLeasingContractUseCase
import org.springframework.stereotype.Controller

/**
 * GraphQL adapter implementing [MasterLeasingContractGraphQLAPI].
 *
 * Maps the input onto a command and delegates. It holds **no rule**: the decimal parsing lives
 * on the input type, every § 2.3 rule lives in the domain, and the three default-applications
 * below are `coding-style.definition.md` § 1.4's requirement that an adapter normalize an
 * incoming null immediately — GraphQL leaves an unsupplied `Boolean` null and **PD-01** gives
 * these three defaults, so absence becomes the default here rather than travelling inward.
 *
 * Annotated `@Controller` because that is how Spring for GraphQL finds a resolver bean. The
 * name is `*GraphQLController`, not `*Controller`, precisely because that annotation collides
 * with the REST role (`coding-style.definition.md` § 3.3).
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 9.
 */
@Controller
class MasterLeasingContractGraphQLController(
    private val createMasterLeasingContractUseCase: CreateMasterLeasingContractUseCase,
) : MasterLeasingContractGraphQLAPI {
    override fun createMasterLeasingContract(
        input: CreateMasterLeasingContractInput,
    ): CreateMasterLeasingContractPayload {
        val result =
            createMasterLeasingContractUseCase.create(
                CreateMasterLeasingContractCommand(
                    employerId = input.employerId,
                    lessorId = input.lessorId,
                    partnerNumber = input.partnerNumber,
                    owner = input.owner,
                    creditLimit = input.creditLimitAsBigDecimal(),
                    contractType = input.contractType,
                    currency = input.currency,
                    eligibleEmployees = input.eligibleEmployees,
                    salesChannel = input.salesChannel,
                    groupJointLiability = input.groupJointLiability ?: false,
                    returnQuotaPercentage = input.returnQuotaPercentageAsBigDecimal(),
                    earlyClaimFeePercentage = input.earlyClaimFeePercentageAsBigDecimal(),
                    earlyClaimWindowMonths = input.earlyClaimWindowMonths,
                    noticePeriodRule = input.noticePeriodRule,
                    paymentTerms = input.paymentTerms,
                    priceRangeMin = input.priceRangeMinAsBigDecimal(),
                    priceRangeMax = input.priceRangeMaxAsBigDecimal(),
                    calculationBasis = input.calculationBasis,
                    servicePackageOptions = input.servicePackageOptions.orEmpty(),
                    servicePackageVersion = input.servicePackageVersion,
                    categoriesEditableInPortal = input.categoriesEditableInPortal ?: false,
                ),
            )

        return CreateMasterLeasingContractPayload(
            masterLeasingContractId = result.masterLeasingContractId,
            status = result.status,
            configurationId = result.configurationId,
            configurationVersion = result.configurationVersion,
        )
    }
}
