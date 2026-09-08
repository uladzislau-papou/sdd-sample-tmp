package com.example.contractmanagement.booking.inbound.rest

import com.example.contractmanagement.booking.core.inport.command.ConfirmTourBookingCommand
import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.inport.usecase.ConfirmTourBookingUseCase
import com.example.contractmanagement.booking.core.inport.usecase.RequestTourBookingUseCase
import com.example.contractmanagement.booking.inbound.rest.request.RequestTourBookingRequest
import com.example.contractmanagement.booking.inbound.rest.response.ConfirmTourBookingResponse
import com.example.contractmanagement.booking.inbound.rest.response.RequestTourBookingResponse
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter implementing [TourBookingRestAPI].
 *
 * Maps request bodies onto commands, delegates to an inbound port, maps results back.
 * That is the whole of its job: it carries no HTTP annotation (those live on the
 * interface), no domain rule, and no error handling ([BookingExceptionHandler] owns that).
 *
 * It depends on `core.inport` interfaces and never on a driver — which is what makes the
 * same core reachable over a second transport without touching it
 * (`coding-style.definition.md` § 3.3).
 */
@RestController
class TourBookingRestController(
    private val requestTourBookingUseCase: RequestTourBookingUseCase,
    private val confirmTourBookingUseCase: ConfirmTourBookingUseCase,
) : TourBookingRestAPI {
    override fun request(request: RequestTourBookingRequest): RequestTourBookingResponse {
        val result =
            requestTourBookingUseCase.request(
                RequestTourBookingCommand(
                    tourId = request.tourId,
                    tourDate = request.tourDate,
                    participantCount = request.participantCount,
                    contactName = request.contactName,
                    contactEmail = request.contactEmail,
                ),
            )
        return RequestTourBookingResponse(result.bookingId, result.status)
    }

    override fun confirm(bookingId: String): ConfirmTourBookingResponse {
        val result = confirmTourBookingUseCase.confirm(ConfirmTourBookingCommand(bookingId))
        return ConfirmTourBookingResponse(result.status)
    }
}
