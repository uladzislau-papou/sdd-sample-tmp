package com.dominikgaller.alpinebooking.guide.inbound.rest;

import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourId;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.GuideTourStatus;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.guide.core.domain.guidetour.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.guide.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.guide.core.inport.usecase.StartTourUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
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
