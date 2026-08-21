package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.guide.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.StartTourUseCase;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.BookingCancellationFailedException;
import com.dominikgaller.alpinebooking.guide.core.inport.command.CancelTourByGuideCommand;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CancelTourByGuideResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CancelTourByGuideUseCase;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.CompleteTourUseCase;
import com.dominikgaller.alpinebooking.guide.core.inport.result.CompleteTourResult;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourCompletedBeforeStartException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer slice tests for {@link GuideTourController}.
 *
 * <p>Uses {@link WebMvcTest} so only the web layer is instantiated: no DataSource, no
 * Flyway, no jOOQ. The inbound port is replaced by a Mockito mock via {@link MockitoBean},
 * so this test asserts HTTP concerns only — routing, status mapping, request validation
 * and the error response contract. {@link GuideExceptionHandler} is picked up
 * because {@code @WebMvcTest} includes {@code @RestControllerAdvice} beans.
 *
 * <p>SDD: slice test per {@code documentation/test.definition.md} section 2.4. Booting the
 * full application here previously created a file-based H2 database under {@code app/data/},
 * because {@code application-test.yml} is profile-specific and the {@code test} profile was
 * never activated.
 */
@WebMvcTest(controllers = GuideTourController.class)
class GuideTourControllerTest {

    private static final String GUIDE_TOUR_ID = GuideTourId.generate().value().toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StartTourUseCase startTourUseCase;

    @MockitoBean
    private CompleteTourUseCase completeTourUseCase;

    @MockitoBean
    private CancelTourByGuideUseCase cancelTourByGuideUseCase;

    // ── UC12: POST /api/v1/guide-tours/{guideTourId}/cancel ───────────────────

    @Test
    void cancel_returns200_withCancelledStatusAndBookingCount() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenReturn(new CancelTourByGuideResult("CANCELLED", 3));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Severe weather warning\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledBookings").value(3));
    }

    @Test
    void cancel_returns200_whenBodyIsAbsent() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenReturn(new CancelTourByGuideResult("CANCELLED", 0));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledBookings").value(0));
    }

    @Test
    void cancel_passesReasonToTheUseCase() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenReturn(new CancelTourByGuideResult("CANCELLED", 1));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Rockfall on the approach\"}"))
                .andExpect(status().isOk());

        final ArgumentCaptor<CancelTourByGuideCommand> captor =
                ArgumentCaptor.forClass(CancelTourByGuideCommand.class);
        verify(cancelTourByGuideUseCase).cancel(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("Rockfall on the approach");
    }

    @Test
    void cancel_returns404_whenGuideTourNotFound() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenThrow(new GuideTourNotFoundException(GUIDE_TOUR_ID));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void cancel_returns409_whenFinished() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenThrow(new InvalidGuideTourStateException(GuideTourStatus.FINISHED));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    /**
     * AC-07's web half. 502, not 500: the guide's request was well-formed and the tour was
     * cancellable, so the failure is downstream and retryable. That the transaction actually
     * rolls back is asserted separately by {@code CancelTourByGuideIT} — this slice mocks the
     * inport and can only verify the mapping.
     */
    @Test
    void cancel_returns502_whenBookingSideFails() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenThrow(new BookingCancellationFailedException(
                        "TOUR-42", new IllegalStateException("booking side down")));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", GUIDE_TOUR_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void cancel_returns400_whenGuideTourIdIsMalformed() throws Exception {
        when(cancelTourByGuideUseCase.cancel(any()))
                .thenThrow(new IllegalArgumentException("Invalid UUID string: not-a-uuid"));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/cancel", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── UC11: POST /api/v1/guide-tours/{guideTourId}/complete ─────────────────

    @Test
    void complete_returns200_withFinishedStatus_whenNoBody() throws Exception {
        when(completeTourUseCase.complete(any()))
                .thenReturn(new CompleteTourResult("FINISHED"));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/complete", GUIDE_TOUR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void complete_returns200_withFinishedStatus_whenCompletedAtProvided() throws Exception {
        when(completeTourUseCase.complete(any()))
                .thenReturn(new CompleteTourResult("FINISHED"));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/complete", GUIDE_TOUR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedAt\": \"2026-06-15T17:30:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void complete_returns404_whenGuideTourNotFound() throws Exception {
        when(completeTourUseCase.complete(any()))
                .thenThrow(new GuideTourNotFoundException(GUIDE_TOUR_ID));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/complete", GUIDE_TOUR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void complete_returns409_whenInvalidState() throws Exception {
        when(completeTourUseCase.complete(any()))
                .thenThrow(new InvalidGuideTourStateException(GuideTourStatus.SCHEDULED));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/complete", GUIDE_TOUR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void complete_returns409_whenCompletedBeforeStart() throws Exception {
        when(completeTourUseCase.complete(any()))
                .thenThrow(new TourCompletedBeforeStartException(
                        Instant.parse("2026-06-15T09:05:00Z"),
                        Instant.parse("2026-06-15T09:00:00Z")));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/complete", GUIDE_TOUR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── UC05: POST /api/v1/guide-tours/{guideTourId}/start ────────────────────

    @Test
    void start_returns200_withRunningStatus_whenNoBody() throws Exception {
        when(startTourUseCase.start(any()))
                .thenReturn(new StartTourResult("RUNNING"));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/start", GUIDE_TOUR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void start_returns200_withRunningStatus_whenStartedAtProvided() throws Exception {
        when(startTourUseCase.start(any()))
                .thenReturn(new StartTourResult("RUNNING"));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/start", GUIDE_TOUR_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedAt\": \"2026-06-15T09:05:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void start_returns404_whenGuideTourNotFound() throws Exception {
        when(startTourUseCase.start(any()))
                .thenThrow(new GuideTourNotFoundException(GUIDE_TOUR_ID));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/start", GUIDE_TOUR_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void start_returns409_whenInvalidState() throws Exception {
        when(startTourUseCase.start(any()))
                .thenThrow(new InvalidGuideTourStateException(GuideTourStatus.RUNNING));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/start", GUIDE_TOUR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void start_returns409_whenTooEarly() throws Exception {
        final Instant scheduledStart = Instant.parse("2026-06-15T09:00:00Z");
        final Instant attemptedAt = Instant.parse("2026-06-15T08:55:00Z");
        when(startTourUseCase.start(any()))
                .thenThrow(new TourStartTooEarlyException(scheduledStart, attemptedAt));

        mockMvc.perform(post("/api/v1/guide-tours/{id}/start", GUIDE_TOUR_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
