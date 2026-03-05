package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.bootstrap.AlpineBookingApplication;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourId;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.GuideTourNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidGuideTourStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.TourStartTooEarlyException;
import com.dominikgaller.alpinebooking.booking.core.domain.GuideTourStatus;
import com.dominikgaller.alpinebooking.booking.core.inport.result.StartTourResult;
import com.dominikgaller.alpinebooking.booking.core.inport.usecase.StartTourUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * Web layer tests for {@link GuideTourController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} to start the full
 * application context with a mock web environment. The use case port is replaced by a
 * Mockito mock via {@link MockitoBean} so no real persistence runs.
 * {@link BookingExceptionHandler} is loaded automatically as part of the context.
 */
@SpringBootTest(classes = AlpineBookingApplication.class)
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
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
