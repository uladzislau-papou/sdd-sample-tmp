package com.jobradleasing.contractmanagement.masterleasing.inbound.graphql

import com.jobradleasing.contractmanagement.masterleasing.core.domain.masterleasingcontract.exception.InvalidMasterLeasingContractException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Maps `masterleasing` domain exceptions to a `GraphQLError` carrying an
 * `ErrorType` classification (architecture.definition.md § 4.5).
 *
 * SDD: See `documentation/use-cases/uc01-register-master-leasing-contract.spec.md` § 3.
 */
@Component
class MasterLeasingContractExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? =
        when (ex) {
            is InvalidMasterLeasingContractException, is IllegalArgumentException ->
                GraphqlErrorBuilder
                    .newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(ex.message)
                    .extensions(mapOf("classification" to ErrorType.BAD_REQUEST.toString()))
                    .build()
            else -> null
        }
}
