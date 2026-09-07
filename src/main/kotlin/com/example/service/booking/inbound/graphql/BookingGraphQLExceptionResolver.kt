package com.example.service.booking.inbound.graphql

import com.example.service.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException
import com.example.service.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.service.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import graphql.GraphQLError
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Translates the same domain exceptions [com.example.service.booking.inbound.rest.BookingExceptionHandler]
 * translates, into GraphQL error classifications instead of HTTP statuses.
 *
 * The two mappings are deliberately separate but must stay consistent in meaning: a
 * conflict is a conflict on both transports. GraphQL has a coarser vocabulary than HTTP —
 * there is no 502 — so an unreachable dependency lands on `INTERNAL_ERROR`, which is
 * honest: from a GraphQL client's point of view it is not their request that was wrong.
 *
 * SDD: see § 3 of the booking use-case specs.
 */
@Component
class BookingGraphQLExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? {
        val errorType =
            when (ex) {
                is InvalidBookingRequestException -> ErrorType.BAD_REQUEST
                is IllegalArgumentException -> ErrorType.BAD_REQUEST
                is BookingNotFoundException -> ErrorType.NOT_FOUND
                is CapacityExceededException -> ErrorType.BAD_REQUEST
                is InvalidBookingStateException -> ErrorType.BAD_REQUEST
                is AvailabilityUnavailableException -> ErrorType.INTERNAL_ERROR
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
