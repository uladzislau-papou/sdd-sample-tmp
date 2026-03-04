package com.dominikgaller.alpinebooking.booking.inbound.rest;

import com.dominikgaller.alpinebooking.booking.bootstrap.AlpineBookingApplication;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.BookingNotFoundException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.CapacityExceededException;
import com.dominikgaller.alpinebooking.booking.core.domain.exception.InvalidBookingStateException;
import com.dominikgaller.alpinebooking.booking.core.domain.TourBookingStatus;
import com.dominikgaller.alpinebooking.booking.core.inport.ConfirmTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.ConfirmTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingResult;
import com.dominikgaller.alpinebooking.booking.core.inport.RequestTourBookingUseCase;
import com.dominikgaller.alpinebooking.booking.core.outport.AvailabilityUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for {@link TourBookingController}.
 *
 * <p>Uses {@link SpringBootTest} with {@link AutoConfigureMockMvc} to start the full
 * application context with a mock web environment. Use case ports are replaced by Mockito
 * mocks via {@link MockitoBean} so no real persistence runs.
 * {@link BookingExceptionHandler} is loaded automatically as part of the context.
 */
@SpringBootTest(classes = AlpineBookingApplication.class)
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
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
}
