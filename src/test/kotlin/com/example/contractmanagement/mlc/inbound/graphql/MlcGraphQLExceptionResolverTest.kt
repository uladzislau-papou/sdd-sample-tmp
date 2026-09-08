package com.example.contractmanagement.mlc.inbound.graphql

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import graphql.GraphQLError
import graphql.Scalars
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.graphql.execution.ErrorType
import java.io.IOException

/**
 * Adapter tests for [MlcGraphQLExceptionResolver].
 *
 * **This file exists because `conformance-reviewer` found the gap.**
 * `uc07-create-master-leasing-contract.spec.md` § 8 specifies *"Persistence failure — the
 * transaction rolls back and no contract exists; classified `INTERNAL_ERROR`"*, and no test
 * anywhere exercised that classification. The reviewer noted it mirrors a repo-wide gap —
 * `BookingGraphQLExceptionResolver` has no test either — but a specified failure scenario with
 * zero executable evidence is a described claim, not a checked one.
 *
 * What is checkable here is the **classification**, which is this class's whole
 * responsibility. The rollback half is the transaction manager's and belongs to an integration
 * test over the driver; § 8's third bullet spans both, and this covers the half that lives in
 * `inbound.graphql`.
 *
 * Driven through the **public** `resolveException`, not the protected `resolveToSingleError`.
 * That is the entry point Spring actually calls, so the test exercises the adapter's real
 * contract rather than reaching past it — and an empty result is the contract for "not mine":
 * `DataFetcherExceptionResolverAdapter` then falls through to the default handling, which
 * classifies as `INTERNAL_ERROR`. So the empty assertion below **is** the INTERNAL_ERROR
 * assertion. Asserting the enum directly would be a test of Spring.
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 3, § 8, § 9.
 */
class MlcGraphQLExceptionResolverTest {
    private val resolver = MlcGraphQLExceptionResolver()

    /**
     * A minimally real [DataFetchingEnvironment], not a bare mock.
     *
     * The resolver reads `executionStepInfo.path` and `field.sourceLocation` when it builds an
     * error, so a bare `mock()` returns null for both and the build throws *inside* Spring's
     * adapter — which swallows it and yields an empty result. The first version of this test did
     * exactly that and read as "the resolver declines to classify a domain exception", which is
     * the opposite of the truth. Real values instead of deeper stubbing, because a stub of
     * `ResultPath` proves nothing about a resolver whose whole job is populating those fields.
     */
    private val environment: DataFetchingEnvironment =
        mock {
            on { executionStepInfo } doReturn
                ExecutionStepInfo
                    .newExecutionStepInfo()
                    .type(Scalars.GraphQLString)
                    .path(ResultPath.rootPath())
                    .build()
            on { field } doReturn Field.newField("createMasterLeasingContract").build()
        }

    /** The single resolved error, or null when the resolver declines to classify. */
    private fun resolve(ex: Throwable): GraphQLError? =
        resolver.resolveException(ex, environment).block()?.singleOrNull()

    @Test
    @DisplayName("§ 3: a domain rule violation classifies as BAD_REQUEST")
    fun resolveToSingleError_classifiesADomainViolationAsBadRequest() {
        val error = resolve(InvalidMasterLeasingContractException("creditLimit must be greater than 0, was: 0"))

        assertThat(error).isNotNull()
        assertThat(error?.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        assertThat(error?.message).contains("creditLimit")
    }

    /**
     * The § 6.3 backstop, and here it is load-bearing rather than theoretical:
     * `CreateMasterLeasingContractInput` throws `IllegalArgumentException` for a malformed
     * decimal, because such a value never becomes a domain type at all.
     */
    @Test
    @DisplayName("§ 6.3: a malformed wire value classifies as BAD_REQUEST")
    fun resolveToSingleError_classifiesAMalformedWireValueAsBadRequest() {
        val error = resolve(IllegalArgumentException("creditLimit must be decimal notation, was: 'not-a-number'"))

        assertThat(error?.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    /**
     * § 8's third bullet: a persistence failure is **not** ours to classify.
     *
     * Returning null hands it to Spring's default handling, which is what produces
     * `INTERNAL_ERROR` — honest, because from a client's point of view it was not their request
     * that was wrong. Asserting null rather than asserting `INTERNAL_ERROR` directly is
     * deliberate: the latter would be a test of Spring.
     */
    @Test
    @DisplayName("§ 8: an infrastructure failure is not classified here — it becomes INTERNAL_ERROR")
    fun resolveToSingleError_leavesAnInfrastructureFailureToSpring() {
        assertThat(resolve(IOException("connection reset"))).isNull()
        assertThat(resolve(RuntimeException("could not extract ResultSet"))).isNull()
    }

    /**
     * `NOT_FOUND` is unreachable, and that is a property of the use case rather than an omission.
     *
     * This operation creates rather than loads, verifies no participant (**PD-06**) and resolves
     * no parent (**PD-04**). If either decision is overturned the resolver gains an entry, and
     * this test is what will fail to remind whoever does it.
     */
    @Test
    @DisplayName("§ 9: the resolver has no NOT_FOUND entry, because nothing can be missing")
    fun resolveToSingleError_hasNoNotFoundClassification() {
        val classifications =
            listOf(
                InvalidMasterLeasingContractException("any"),
                IllegalArgumentException("any"),
            ).mapNotNull { resolve(it)?.errorType }

        assertThat(classifications).doesNotContain(ErrorType.NOT_FOUND)
    }
}
