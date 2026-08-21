package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.inbound.rest.request.CompleteTourRequest;
import com.dominikgaller.alpinebooking.guide.inbound.rest.request.StartTourRequest;
import com.dominikgaller.alpinebooking.guide.inbound.rest.response.CompleteTourResponse;
import com.dominikgaller.alpinebooking.guide.inbound.rest.response.StartTourResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Inbound REST contract for guide tour operations.
 *
 * <p>Defines the HTTP surface (routes, methods, status codes, parameter bindings).
 * The implementation ({@link GuideTourController}) contains no HTTP annotations —
 * all delivery concerns live here.
 *
 * <p>SDD: See {@code documentation/use-cases/uc05-start-tour.spec.md}.
 */
@RequestMapping("/api/v1/guide-tours")
public interface GuideTourRestAPI {

    /**
     * UC05 – Starts a scheduled guide tour.
     *
     * @param guideTourId the UUID of the guide tour to start
     * @param request     optional body; if absent or {@code startedAt} is null, clock time is used
     * @return HTTP 200 with the new status
     */
    @PostMapping("/{guideTourId}/start")
    StartTourResponse start(
            @PathVariable String guideTourId,
            @RequestBody(required = false) StartTourRequest request);

    @PostMapping("/{guideTourId}/complete")
    CompleteTourResponse complete(
            @PathVariable String guideTourId,
            @RequestBody(required = false) CompleteTourRequest request);
}
