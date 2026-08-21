package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.inbound.rest.request.CancelTourBookingRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.request.ChangeParticipantsRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.request.RequestTourBookingRequest;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.CancelTourBookingResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.ChangeParticipantsResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.ConfirmTourBookingResponse;
import com.dominikgaller.alpinebooking.booking.inbound.rest.response.RequestTourBookingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Inbound REST contract for tour booking operations.
 *
 * <p>Defines the HTTP surface (routes, methods, status codes, parameter bindings).
 * The implementation ({@link TourBookingController}) contains no HTTP annotations —
 * all delivery concerns live here.
 *
 * <p>SDD: See {@code documentation/use-cases/uc01-request-tour-booking.spec.md},
 *          {@code documentation/use-cases/uc02-confirm-tour-booking.spec.md},
 *          {@code documentation/use-cases/uc03-cancel-tour-booking.spec.md}, and
 *          {@code documentation/use-cases/uc04-change-participants.spec.md}.
 */
@RequestMapping("/api/v1/bookings")
public interface TourBookingRestAPI {

    /**
     * UC01 – Creates a new tour booking request.
     *
     * @param request validated request body
     * @return HTTP 201 with booking id and status
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RequestTourBookingResponse request(@Valid @RequestBody RequestTourBookingRequest request);

    /**
     * UC02 – Confirms an existing tour booking.
     *
     * @param bookingId the UUID of the booking to confirm
     * @return HTTP 200 with updated status
     */
    @PostMapping("/{bookingId}/confirm")
    ConfirmTourBookingResponse confirm(@PathVariable String bookingId);

    /**
     * UC03/UC08 – Cancels an existing tour booking, recording the participant as the
     * initiator and optionally why.
     *
     * <p>{@code POST}, not {@code DELETE}: cancelling is a state transition that leaves the
     * booking addressable rather than a removal, and a {@code DELETE} body — which is how
     * {@code reason} arrives — is dropped by some clients and intermediaries. Replaced the
     * former {@code DELETE /{bookingId}} route in the UC08 increment; see
     * {@code documentation/use-cases/uc08-cancel-booking-by-user.spec.md} section 9.
     *
     * @param bookingId the UUID of the booking to cancel
     * @param request   optional body carrying {@code cancelledAt} and {@code reason};
     *                  may be absent entirely
     * @return HTTP 200 with updated status
     */
    @PostMapping("/{bookingId}/cancel")
    CancelTourBookingResponse cancel(
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelTourBookingRequest request);

    /**
     * UC04 – Changes the participant count of an existing tour booking.
     *
     * @param bookingId the UUID of the booking to update
     * @param request   validated request body containing the new participant count
     * @return HTTP 200 with updated participant count
     */
    @PatchMapping("/{bookingId}/participants")
    ChangeParticipantsResponse changeParticipants(
            @PathVariable String bookingId,
            @Valid @RequestBody ChangeParticipantsRequest request);

}
