package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingRequestException;
import com.dominikgaller.alpinebooking.booking.core.inport.command.CancelTourBookingCommand;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.inport.result.CancelTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.CancelTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ChangeParticipantsResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.ChangeParticipantsUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.result.ConfirmTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.ConfirmTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.result.RequestTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.RequestTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer slice tests for {@link TourBookingController}.
 *
 * <p>Uses {@link WebMvcTest} so only the web layer is instantiated: no DataSource, no
 * Flyway, no jOOQ. Inbound ports are replaced by Mockito mocks via {@link MockitoBean},
 * so this test asserts HTTP concerns only — routing, status mapping, request validation
 * and the error response contract. {@link BookingExceptionHandler} is picked up because
 * {@code @WebMvcTest} includes {@code @RestControllerAdvice} beans.
 *
 * <p>SDD: slice test per {@code documentation/test.definition.md} section 2.4. Booting the
 * full application here previously created a file-based H2 database under {@code app/data/},
 * because {@code application-test.yml} is profile-specific and the {@code test} profile was
 * never activated.
 */
@WebMvcTest(controllers = TourBookingController.class)
class TourBookingControllerTest {

    private static final String VALID_BODY = """
            {
              "tourId": "TOUR-42",
              "tourDate": "2026-06-15",
              "participantCount": 3,
              "contactName": "Alice",
              "contactEmail": "alice@example.com"
            }
            """;

    private static final String BOOKING_UUID = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestTourBookingUseCase useCase;

    @MockitoBean
    private ConfirmTourBookingUseCase confirmUseCase;

    @MockitoBean
    private CancelTourBookingUseCase cancelUseCase;

    @MockitoBean
    private ChangeParticipantsUseCase changeParticipantsUseCase;

    // ── UC01: POST /api/v1/bookings ───────────────────────────────────────────

    @Test
    void postWithValidBody_returns201WithBookingIdAndStatus() throws Exception {
        when(useCase.request(any()))
                .thenReturn(new RequestTourBookingResult("test-uuid", "REQUESTED"));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value("test-uuid"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void postWithMissingTourId_returns400() throws Exception {
        final String bodyMissingTourId = """
                {
                  "tourDate": "2026-06-15",
                  "participantCount": 3,
                  "contactName": "Alice",
                  "contactEmail": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingTourId))
                .andExpect(status().isBadRequest());
    }

    /**
     * UC01 AC-03 at the HTTP boundary. {@code @Min(1)} on
     * {@code RequestTourBookingRequest.participantCount} is a syntactic rule, so Bean
     * Validation rejects it before the use case is reached.
     */
    @Test
    void postWithParticipantCountBelowMinimum_returns400() throws Exception {
        final String bodyWithZeroParticipants = """
                {
                  "tourId": "TOUR-42",
                  "tourDate": "2026-06-15",
                  "participantCount": 0,
                  "contactName": "Alice",
                  "contactEmail": "alice@example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithZeroParticipants))
                .andExpect(status().isBadRequest());
    }

    /**
     * UC01 AC-04 at the HTTP boundary.
     *
     * <p>"Tour date must be in the future" is a <em>semantic</em> rule that depends on
     * the current time, so the domain owns it ({@code TourDate} + {@code ClockPort}) and
     * there is deliberately no {@code @Future} annotation on the request DTO — that would
     * duplicate the rule outside the domain. What the web layer owns, and what this test
     * asserts, is the <em>mapping</em>: {@code InvalidBookingRequestException} → 400.
     */
    @Test
    void postWithInvalidBookingRequestFromDomain_returns400() throws Exception {
        when(useCase.request(any()))
                .thenThrow(new InvalidBookingRequestException("Tour date must be in the future"));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    /**
     * A malformed path variable, or any value-object guard that throws
     * {@code IllegalArgumentException}, is a client error rather than a server fault.
     * Unmapped it surfaces as 500.
     *
     * <p>What the web layer owns is the <em>mapping</em>, which is what this asserts. The
     * throw itself happens in the driver — {@code UUID.fromString(command.bookingId())} —
     * and is covered by {@code ConfirmTourBookingDriverTest}. A slice test cannot reach it,
     * because the inport is mocked here.
     *
     * <p>SDD: {@code coding-style.definition.md} section 6.2,
     *          {@code ports/start-tour.inport.spec.md} section 7.
     */
    @Test
    void confirmWithIllegalArgument_returns400() throws Exception {
        when(confirmUseCase.confirm(any()))
                .thenThrow(new IllegalArgumentException("Invalid UUID string: not-a-uuid"));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", BOOKING_UUID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void postWithCapacityExceeded_returns409() throws Exception {
        when(useCase.request(any()))
                .thenThrow(new CapacityExceededException(5, 2));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void postWithAvailabilityFailure_returns502() throws Exception {
        when(useCase.request(any()))
                .thenThrow(new AvailabilityUnavailableException("infrastructure failure"));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── UC02: POST /api/v1/bookings/{bookingId}/confirm ───────────────────────

    @Test
    void confirmBooking_returns200_withConfirmedStatus() throws Exception {
        when(confirmUseCase.confirm(any()))
                .thenReturn(new ConfirmTourBookingResult("CONFIRMED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/confirm", BOOKING_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmBooking_returns404_whenNotFound() throws Exception {
        when(confirmUseCase.confirm(any()))
                .thenThrow(new BookingNotFoundException(BOOKING_UUID));

        mockMvc.perform(post("/api/v1/bookings/{id}/confirm", BOOKING_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void confirmBooking_returns409_whenInvalidState() throws Exception {
        when(confirmUseCase.confirm(any()))
                .thenThrow(new InvalidBookingStateException(TourBookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/bookings/{id}/confirm", BOOKING_UUID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── UC03/UC08: POST /api/v1/bookings/{bookingId}/cancel ───────────────────

    @Test
    void cancelBooking_returns200_withCancelledStatus() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenReturn(new CancelTourBookingResult("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /**
     * UC08 — a body of {@code {}} must behave exactly like no body at all.
     *
     * <p>Distinct from {@code cancelBooking_returns200_withCancelledStatus} above, which
     * sends no body and exercises the {@code @RequestBody(required = false)} null the
     * controller normalises. This one sends a real but empty object, so the controller
     * receives a non-null {@code CancelTourBookingRequest} with both components null — a
     * different branch of the same normalisation. An earlier revision of this test issued a
     * byte-identical request to the one above and therefore asserted nothing new.
     */
    @Test
    void cancelBooking_returns200_whenBodyIsAnEmptyObject() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenReturn(new CancelTourBookingResult("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        final ArgumentCaptor<CancelTourBookingCommand> captor =
                ArgumentCaptor.forClass(CancelTourBookingCommand.class);
        verify(cancelUseCase).cancel(captor.capture());
        assertThat(captor.getValue().reason()).isNull();
    }

    /**
     * UC08 — the reason and cancellation time must reach the inport. Asserted with a captor
     * rather than by observing the response, because the response carries only the status:
     * a controller that dropped the body would look identical from the outside.
     */
    @Test
    void cancelBooking_passesReasonToTheUseCase() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenReturn(new CancelTourBookingResult("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Travel plans changed\"}"))
                .andExpect(status().isOk());

        final ArgumentCaptor<CancelTourBookingCommand> captor =
                ArgumentCaptor.forClass(CancelTourBookingCommand.class);
        verify(cancelUseCase).cancel(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("Travel plans changed");
    }

    /**
     * The cancellation time is the system's observation, not the caller's claim
     * ({@code architecture.definition.md} § 8.1). A client that sends one must not have it
     * honoured — {@code CancelTourBookingRequest} has no such component, so Jackson has
     * nowhere to bind it and the driver falls through to {@code ClockPort}.
     *
     * <p>Asserted rather than assumed because the failure mode is silent: if the DTO ever
     * regains a {@code cancelledAt}, every other test here still passes while an HTTP
     * caller gains the ability to backdate a cancellation.
     */
    @Test
    void cancelBooking_ignoresAClientSuppliedCancelledAt() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenReturn(new CancelTourBookingResult("CANCELLED"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledAt\":\"1999-01-01T00:00:00Z\",\"reason\":\"x\"}"))
                .andExpect(status().isOk());

        final ArgumentCaptor<CancelTourBookingCommand> captor =
                ArgumentCaptor.forClass(CancelTourBookingCommand.class);
        verify(cancelUseCase).cancel(captor.capture());
        assertThat(captor.getValue()).extracting("reason").isEqualTo("x");
        assertThat(captor.getValue().getClass().getRecordComponents())
                .noneMatch(rc -> rc.getName().equals("cancelledAt"));
    }

    /**
     * UC08 — a blank or over-long reason is a 400. The validation itself lives in
     * `CancellationReason` and is exercised by `CancellationReasonTest` and the driver test;
     * this slice mocks the inport, so what it verifies is the other half — that
     * `BookingExceptionHandler` maps the resulting exception to 400 with an error body.
     */
    @Test
    void cancelBooking_returns400_whenReasonIsInvalid() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenThrow(new InvalidBookingRequestException(
                        "Cancellation reason must not be blank"));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void cancelBooking_returns404_whenNotFound() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenThrow(new BookingNotFoundException(BOOKING_UUID));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void cancelBooking_returns409_whenInvalidState() throws Exception {
        when(cancelUseCase.cancel(any()))
                .thenThrow(new InvalidBookingStateException(TourBookingStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_UUID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── UC04: PATCH /api/v1/bookings/{bookingId}/participants ─────────────────

    @Test
    void changeParticipants_returns200_withUpdatedCount() throws Exception {
        when(changeParticipantsUseCase.change(any()))
                .thenReturn(new ChangeParticipantsResult(5));

        mockMvc.perform(patch("/api/v1/bookings/{id}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(5));
    }

    @Test
    void changeParticipants_returns404_whenNotFound() throws Exception {
        when(changeParticipantsUseCase.change(any()))
                .thenThrow(new BookingNotFoundException(BOOKING_UUID));

        mockMvc.perform(patch("/api/v1/bookings/{id}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 5}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void changeParticipants_returns409_whenInvalidState() throws Exception {
        when(changeParticipantsUseCase.change(any()))
                .thenThrow(new InvalidBookingStateException(TourBookingStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/bookings/{id}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    /**
     * UC04 400. {@code @Min(1)} on {@code ChangeParticipantsRequest.newParticipantCount}
     * is a syntactic rule, rejected before the use case is reached.
     */
    @Test
    void changeParticipants_returns400_whenCountBelowMinimum() throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/{bookingId}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * UC04 502. Mirrors {@code postWithAvailabilityFailure_returns502} — an availability
     * infrastructure failure is not a client error.
     */
    @Test
    void changeParticipants_returns502_whenAvailabilityUnavailable() throws Exception {
        when(changeParticipantsUseCase.change(any()))
                .thenThrow(new AvailabilityUnavailableException("infrastructure failure"));

        mockMvc.perform(patch("/api/v1/bookings/{bookingId}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 5}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void changeParticipants_returns409_whenCapacityExceeded() throws Exception {
        when(changeParticipantsUseCase.change(any()))
                .thenThrow(new CapacityExceededException(10, 2));

        mockMvc.perform(patch("/api/v1/bookings/{id}/participants", BOOKING_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParticipantCount\": 10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

}
