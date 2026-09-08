package com.example.contractmanagement.guide.inbound.rest

import com.example.contractmanagement.guide.core.inport.command.StartTourCommand
import com.example.contractmanagement.guide.core.inport.usecase.StartTourUseCase
import com.example.contractmanagement.guide.inbound.rest.request.StartTourRequest
import com.example.contractmanagement.guide.inbound.rest.response.StartTourResponse
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter implementing [GuideTourRestAPI].
 *
 * An absent body is normalized to an absent timestamp rather than rejected: starting
 * without saying when is the common case, and the driver already knows how to resolve it.
 * Normalizing at the adapter is what keeps the null from travelling inward as a surprise
 * (`coding-style.definition.md` § 1.4).
 */
@RestController
class GuideTourRestController(
    private val startTourUseCase: StartTourUseCase,
) : GuideTourRestAPI {
    override fun start(
        guideTourId: String,
        request: StartTourRequest?,
    ): StartTourResponse {
        val result = startTourUseCase.start(StartTourCommand(guideTourId, request?.startedAt))
        return StartTourResponse(result.status)
    }
}
