package com.example.contractmanagement.mlc.inbound.graphql

import com.example.contractmanagement.mlc.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import graphql.GraphQLError
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Translates the `mlc` context's domain exceptions into GraphQL error classifications.
 *
 * Two entries, which is `uc07` § 9's whole list:
 *
 * - [InvalidMasterLeasingContractException] → `BAD_REQUEST`. One domain exception covers every
 *   § 2.3 rule, which is what lets the classification list stay complete while **PD-01** and
 *   **PD-10** are still provisional: when a rule changes, no consumer-visible error moves.
 * - [IllegalArgumentException] → `BAD_REQUEST`. The backstop `coding-style.definition.md`
 *   § 6.3 requires in every resolver, and here it is load-bearing rather than theoretical:
 *   `CreateMasterLeasingContractInput` throws it for a malformed decimal, because such a value
 *   never becomes a domain type at all.
 *
 * `NOT_FOUND` is deliberately absent. This operation creates rather than loads, verifies no
 * participant (**PD-06**) and resolves no parent (**PD-04**) — so nothing can be missing.
 *
 * A persistence failure is not a domain exception and is not mapped here; it surfaces as
 * `INTERNAL_ERROR`, which is honest — from a client's point of view it was not their request
 * that was wrong.
 *
 * Separate from the booking context's resolver on purpose: a resolver translates the
 * exceptions of the context that owns them, and `architecture.definition.md` § 11 rule 3
 * closes a context's `core.domain` to every other context.
 *
 * SDD: see `documentation/use-cases/uc07-create-master-leasing-contract.spec.md` § 3, § 9.
 */
@Component
class MlcGraphQLExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? {
        val errorType =
            when (ex) {
                is InvalidMasterLeasingContractException -> ErrorType.BAD_REQUEST
                is IllegalArgumentException -> ErrorType.BAD_REQUEST
                else -> return null
            }

        return GraphQLError
            .newError()
            .errorType(errorType)
            .message(ex.message)
            .path(env.executionStepInfo.path)
            .location(env.field.sourceLocation)
            .build()
    }
}
