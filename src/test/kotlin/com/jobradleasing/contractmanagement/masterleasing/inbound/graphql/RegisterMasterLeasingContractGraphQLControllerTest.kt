package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import com.jobradleasing.contractmanagement.masterleasing.core.inport.result.RegisterMasterLeasingContractResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.graphql.test.tester.GraphQlTester

@GraphQlTest(RegisterMasterLeasingContractGraphQLController::class)
class RegisterMasterLeasingContractGraphQLControllerTest {
    @TestConfiguration
    class Config {
        @Bean
        fun useCase(): StubRegisterMasterLeasingContractUseCase = StubRegisterMasterLeasingContractUseCase()

        @Bean
        fun exceptionResolver(): MasterLeasingContractExceptionResolver = MasterLeasingContractExceptionResolver()
    }

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @Autowired
    private lateinit var useCase: StubRegisterMasterLeasingContractUseCase

    private val document =
        """
        mutation Register(${'$'}input: RegisterMasterLeasingContractInput!) {
          registerMasterLeasingContract(input: ${'$'}input) {
            masterLeasingContractId
            status
            configurationVersion
          }
        }
        """.trimIndent()

    private fun aValidInputVariables(): Map<String, Any?> =
        mapOf(
            "employerId" to "employer-1",
            "lessorId" to "lessor-1",
            "partnerNumber" to "PN-0001",
            "parentMasterLeasingContractId" to null,
            "configuration" to
                mapOf(
                    "contractType" to "SALARY_SACRIFICE",
                    "salesChannel" to "DIRECT",
                    "inheritanceMode" to "COPIED_ONCE",
                    "currency" to "EUR",
                    "creditLimitAmount" to "50000.0000",
                    "priceRangeMin" to "500.0000",
                    "priceRangeMax" to "3000.0000",
                    "eligibleEmployees" to 100,
                    "jointLiability" to false,
                    "returnQuotaPercentage" to "10",
                    "noticePeriodMonths" to 3,
                ),
        )

    @BeforeEach
    fun resetStub() {
        useCase.result = null
        useCase.exceptionToThrow = null
    }

    @Test
    fun register_returnsPayload_onSuccess() {
        useCase.result =
            RegisterMasterLeasingContractResult(
                masterLeasingContractId = "3f1c1b1e-0000-0000-0000-000000000000",
                status = "DRAFT",
                configurationVersion = 1,
            )

        graphQlTester
            .document(document)
            .variable("input", aValidInputVariables())
            .execute()
            .path("registerMasterLeasingContract.status")
            .entity(String::class.java)
            .isEqualTo("DRAFT")
            .path("registerMasterLeasingContract.configurationVersion")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    @Test
    fun register_returnsBadRequest_whenPriceBandInverted() {
        useCase.exceptionToThrow =
            InvalidMasterLeasingContractException(
                "priceRangeMin must not exceed priceRangeMax",
            )

        graphQlTester
            .document(document)
            .variable("input", aValidInputVariables())
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors.single().extensions["classification"]).isEqualTo("BAD_REQUEST")
            }
    }

    @Test
    fun register_returnsBadRequest_whenIllegalArgumentExceptionThrown() {
        useCase.exceptionToThrow = IllegalArgumentException("malformed parent id")

        graphQlTester
            .document(document)
            .variable("input", aValidInputVariables())
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors.single().extensions["classification"]).isEqualTo("BAD_REQUEST")
            }
    }

    @Test
    fun schema_exposesNoVersionField() {
        val introspection =
            """
            query {
              __type(name: "MlcConfigurationInput") {
                inputFields { name }
              }
            }
            """.trimIndent()

        val names =
            graphQlTester
                .document(introspection)
                .execute()
                .path("__type.inputFields[*].name")
                .entityList(String::class.java)
                .get()

        assertThat(names).doesNotContain("version")
    }
}
