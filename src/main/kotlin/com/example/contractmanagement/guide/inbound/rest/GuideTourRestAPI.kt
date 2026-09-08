package com.example.contractmanagement.guide.inbound.rest

import com.example.contractmanagement.guide.inbound.rest.request.StartTourRequest
import com.example.contractmanagement.guide.inbound.rest.response.StartTourResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Inbound REST contract for guide tour operations.
 *
 * SDD: see `documentation/use-cases/uc05-start-tour.spec.md` § 9.
 */
@RequestMapping("/api/v1/guide-tours")
interface GuideTourRestAPI {
    /**
     * UC05 — starts a scheduled tour. Returns 200 with the updated status.
     *
     * The body is optional: a guide starting a tour now sends none.
     */
    @PostMapping("/{guideTourId}/start")
    fun start(
        @PathVariable guideTourId: String,
        @RequestBody(required = false) request: StartTourRequest?,
    ): StartTourResponse
}
