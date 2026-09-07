package com.example.service.booking.inbound.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping

/**
 * Inbound GraphQL contract for tour booking operations.
 *
 * Holds the GraphQL surface — field mappings and argument binding — so the adapter
 * implementing it carries no GraphQL annotation, mirroring the REST split
 * (`coding-style.definition.md` § 3.3).
 *
 * This is **not** the inbound port. The inbound port is
 * `core.inport.usecase.RequestTourBookingUseCase`, and it is the *same* port the REST
 * adapter calls. That is the point of the pair: the core does not know its transport.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 9.
 */
interface TourBookingGraphQLAPI {
    /**
     * Resolves the schema's mandatory `Query` root.
     *
     * Present because the GraphQL specification requires a `Query` operation and this
     * template has no read side. It is an infrastructure fact, not a domain read — which
     * is why it does not go through an inbound port. Replace it with a real query.
     */
    @QueryMapping
    fun apiVersion(): String

    @MutationMapping
    fun requestTourBooking(
        @Argument input: RequestTourBookingInput,
    ): RequestTourBookingPayload
}
