package com.example.contractmanagement.mlc.inbound.graphql

import com.example.contractmanagement.mlc.core.inport.command.CreateMasterLeasingContractCommand
import com.example.contractmanagement.mlc.core.inport.result.CreateMasterLeasingContractResult
import com.example.contractmanagement.mlc.core.inport.usecase.CreateMasterLeasingContractUseCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal

/**
 * Adapter tests for [MasterLeasingContractGraphQLController] (UC07).
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 9.
 *
 * These assert **mapping only**. `test.definition.md` § 8 forbids a controller test that
 * validates business logic instead of use case behaviour, so every rule in § 2.3 is asserted
 * at its value object or at the driver, never here.
 *
 * What is worth asserting here is the part the driver cannot see: the schema declares the
 * monetary and percentage fields as `String` because no decimal scalar is declared, so this
 * adapter parses them. A parse that silently produced the wrong scale — or a `Double`
 * round-trip — would leave every domain and driver test green.
 */
class MasterLeasingContractGraphQLControllerTest {
    private val useCase: CreateMasterLeasingContractUseCase =
        mock {
            on { create(any()) } doReturn
                CreateMasterLeasingContractResult(
                    masterLeasingContractId = "11111111-1111-1111-1111-111111111111",
                    status = "ACTIVE",
                    configurationId = "22222222-2222-2222-2222-222222222222",
                    configurationVersion = 1,
                )
        }

    private val controller = MasterLeasingContractGraphQLController(useCase)

    private val fullInput =
        CreateMasterLeasingContractInput(
            employerId = "emp-1",
            lessorId = "lessor-1",
            partnerNumber = "p-4711",
            owner = "contract-operations",
            creditLimit = "50000.00",
            contractType = "salary-sacrifice-leasing",
            currency = "EUR",
            eligibleEmployees = 250,
            salesChannel = "direct",
            groupJointLiability = true,
            returnQuotaPercentage = "10",
            earlyClaimFeePercentage = "2.5",
            earlyClaimWindowMonths = 12,
            noticePeriodRule = "3-months-to-quarter-end",
            paymentTerms = "NET30",
            priceRangeMin = "749",
            priceRangeMax = "11000",
            calculationBasis = "sale-price-plus-shipping",
            servicePackageOptions = listOf("basic", "comfort"),
            servicePackageVersion = 3,
            categoriesEditableInPortal = true,
        )

    @Test
    fun createMasterLeasingContract_mapsInputToCommand() {
        controller.createMasterLeasingContract(fullInput)

        val captor = argumentCaptor<CreateMasterLeasingContractCommand>()
        verify(useCase).create(captor.capture())
        val command = captor.firstValue

        assertThat(command.employerId).isEqualTo("emp-1")
        assertThat(command.lessorId).isEqualTo("lessor-1")
        assertThat(command.partnerNumber).isEqualTo("p-4711")
        assertThat(command.owner).isEqualTo("contract-operations")
        assertThat(command.creditLimit).isEqualByComparingTo(BigDecimal("50000.00"))
        assertThat(command.contractType).isEqualTo("salary-sacrifice-leasing")
        assertThat(command.currency).isEqualTo("EUR")
        assertThat(command.eligibleEmployees).isEqualTo(250)
        assertThat(command.salesChannel).isEqualTo("direct")
        assertThat(command.groupJointLiability).isTrue()
        assertThat(command.returnQuotaPercentage).isEqualByComparingTo(BigDecimal("10"))
        assertThat(command.earlyClaimFeePercentage).isEqualByComparingTo(BigDecimal("2.5"))
        assertThat(command.earlyClaimWindowMonths).isEqualTo(12)
        assertThat(command.noticePeriodRule).isEqualTo("3-months-to-quarter-end")
        assertThat(command.paymentTerms).isEqualTo("NET30")
        assertThat(command.priceRangeMin).isEqualByComparingTo(BigDecimal("749"))
        assertThat(command.priceRangeMax).isEqualByComparingTo(BigDecimal("11000"))
        assertThat(command.calculationBasis).isEqualTo("sale-price-plus-shipping")
        assertThat(command.servicePackageOptions).containsExactly("basic", "comfort")
        assertThat(command.servicePackageVersion).isEqualTo(3)
        assertThat(command.categoriesEditableInPortal).isTrue()
    }

    @Test
    fun createMasterLeasingContract_mapsResultToPayload() {
        val payload = controller.createMasterLeasingContract(fullInput)

        assertThat(payload.masterLeasingContractId).isEqualTo("11111111-1111-1111-1111-111111111111")
        assertThat(payload.status).isEqualTo("ACTIVE")
        assertThat(payload.configurationId).isEqualTo("22222222-2222-2222-2222-222222222222")
        assertThat(payload.configurationVersion).isEqualTo(1)
    }

    /**
     * The absent-optional path, mirroring AC-08 at the transport.
     *
     * `coding-style.definition.md` § 1.4 requires an adapter to normalize incoming nulls
     * immediately. The two `Boolean`s and the list are where that bites: GraphQL leaves an
     * unsupplied `Boolean` null, and **PD-01** says they default to `false` and to empty — so
     * the adapter, not the domain, is what turns absence into the default.
     */
    @Test
    fun createMasterLeasingContract_normalisesAbsentOptionals() {
        val minimal =
            CreateMasterLeasingContractInput(
                employerId = "emp-1",
                lessorId = "lessor-1",
                creditLimit = "50000",
                contractType = "salary-sacrifice-leasing",
                currency = "EUR",
                eligibleEmployees = 250,
            )

        controller.createMasterLeasingContract(minimal)

        val captor = argumentCaptor<CreateMasterLeasingContractCommand>()
        verify(useCase).create(captor.capture())
        val command = captor.firstValue

        assertThat(command.partnerNumber).isNull()
        assertThat(command.owner).isNull()
        assertThat(command.returnQuotaPercentage).isNull()
        assertThat(command.priceRangeMin).isNull()
        assertThat(command.priceRangeMax).isNull()
        assertThat(command.servicePackageVersion).isNull()
        assertThat(command.groupJointLiability).isFalse()
        assertThat(command.categoriesEditableInPortal).isFalse()
        assertThat(command.servicePackageOptions).isEmpty()
    }

    /**
     * A malformed decimal is rejected at the adapter, not carried inward.
     *
     * It surfaces as a `BAD_REQUEST`-equivalent GraphQL error through the
     * `IllegalArgumentException` backstop that every exception resolver maps
     * (`coding-style.definition.md` § 6.3) — the same route as identifier parsing. It is not a
     * § 2.3 rule and deliberately not a domain exception: the value never becomes a domain
     * type at all.
     */
    @Test
    fun createMasterLeasingContract_rejectsAMalformedDecimal() {
        assertThatThrownBy {
            controller.createMasterLeasingContract(fullInput.copy(creditLimit = "not-a-number"))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
