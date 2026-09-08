package com.example.contractmanagement.booking.inbound.graphql

import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.inport.usecase.RequestTourBookingUseCase
import org.springframework.stereotype.Controller

/**
 * GraphQL adapter implementing [TourBookingGraphQLAPI].
 *
 * Calls the **same** inbound port as `TourBookingRestController`. Neither adapter knows
 * about the other, and the core knows about neither — which is the whole claim of Ports &
 * Adapters, made checkable here rather than asserted in prose.
 *
 * Annotated `@Controller` because that is how Spring for GraphQL finds a resolver bean.
 * The name is `*GraphQLController`, not `*Controller`, precisely because that annotation
 * collides with the REST role (`coding-style.definition.md` § 3.3).
 */
@Controller
class TourBookingGraphQLController(
    private val requestTourBookingUseCase: RequestTourBookingUseCase,
) : TourBookingGraphQLAPI {
    override fun apiVersion(): String = API_VERSION

    override fun requestTourBooking(input: RequestTourBookingInput): RequestTourBookingPayload {
        val result =
            requestTourBookingUseCase.request(
                RequestTourBookingCommand(
                    tourId = input.tourId,
                    tourDate = input.tourDateAsLocalDate(),
                    participantCount = input.participantCount,
                    contactName = input.contactName,
                    contactEmail = input.contactEmail,
                ),
            )
        return RequestTourBookingPayload(result.bookingId, result.status)
    }

    private companion object {
        const val API_VERSION = "v1"
    }
}
