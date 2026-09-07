package com.example.service.booking.inbound.rest

import com.example.service.booking.core.domain.tourbooking.TourBookingStatus
import com.example.service.booking.core.domain.tourbooking.exception.AvailabilityUnavailableException
import com.example.service.booking.core.domain.tourbooking.exception.BookingNotFoundException
import com.example.service.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.service.booking.core.domain.tourbooking.exception.InvalidBookingStateException
import com.example.service.booking.core.inport.command.RequestTourBookingCommand
import com.example.service.booking.core.inport.result.ConfirmTourBookingResult
import com.example.service.booking.core.inport.result.RequestTourBookingResult
import com.example.service.booking.core.inport.usecase.ConfirmTourBookingUseCase
import com.example.service.booking.core.inport.usecase.RequestTourBookingUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * Web slice tests for [TourBookingRestController].
 *
 * `@WebMvcTest` instantiates the web layer only — no DataSource, no Flyway, no JPA — and
 * the inbound ports are replaced by mocks. So this test asserts HTTP concerns and nothing
 * else: routing, status mapping, request validation and the error contract.
 * [BookingExceptionHandler] is included because `@WebMvcTest` picks up
 * `@RestControllerAdvice` beans.
 *
 * Unlike the Java original this needs no test-only `@SpringBootConfiguration`:
 * `ServiceApplication` now lives in the root package, so `@WebMvcTest`'s upward search
 * finds it.
 *
 * SDD: slice test per `documentation/test.definition.md` § 2.4.
 */
@WebMvcTest(controllers = [TourBookingRestController::class])
class TourBookingRestControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var requestTourBookingUseCase: RequestTourBookingUseCase

    @MockitoBean
    private lateinit var confirmTourBookingUseCase: ConfirmTourBookingUseCase

    private val bookingUuid: String = UUID.randomUUID().toString()

    private val validBody =
        """
        {
          "tourId": "TOUR-42",
          "tourDate": "2026-06-15",
          "participantCount": 3,
          "contactName": "Alice",
          "contactEmail": "alice@example.com"
        }
        """.trimIndent()

    private fun postBooking(body: String = validBody) =
        mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body))

    @Test
    fun request_returns201_withBookingIdAndStatus() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenReturn(RequestTourBookingResult(bookingUuid, TourBookingStatus.REQUESTED.name))

        postBooking()
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.bookingId").value(bookingUuid))
            .andExpect(jsonPath("$.status").value("REQUESTED"))
    }

    @Test
    fun request_mapsTheBodyOntoTheCommand_withoutAlteringIt() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenReturn(RequestTourBookingResult(bookingUuid, TourBookingStatus.REQUESTED.name))

        postBooking()

        val captor = argumentCaptor<RequestTourBookingCommand>()
        verify(requestTourBookingUseCase).request(captor.capture())
        assertThat(captor.firstValue).isEqualTo(
            RequestTourBookingCommand(
                tourId = "TOUR-42",
                tourDate = LocalDate.parse("2026-06-15"),
                participantCount = 3,
                contactName = "Alice",
                contactEmail = "alice@example.com",
            ),
        )
    }

    @Test
    fun request_returns400_whenParticipantCountIsBelowOne() {
        postBooking(validBody.replace("\"participantCount\": 3", "\"participantCount\": 0"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun request_returns400_whenTourIdIsBlank() {
        postBooking(validBody.replace("\"TOUR-42\"", "\"  \""))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun request_returns400_whenTheDomainRejectsTheRequest() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenThrow(InvalidBookingRequestException("Tour date must be in the future"))

        postBooking()
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Tour date must be in the future"))
    }

    @Test
    fun request_returns409_whenCapacityIsExceeded() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenThrow(CapacityExceededException(5, 2))

        postBooking().andExpect(status().isConflict)
    }

    @Test
    fun request_returns502_whenAvailabilityIsUnreachable() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenThrow(AvailabilityUnavailableException("availability system unreachable"))

        postBooking().andExpect(status().isBadGateway)
    }

    @Test
    fun confirm_returns200_withConfirmedStatus() {
        whenever(confirmTourBookingUseCase.confirm(any()))
            .thenReturn(ConfirmTourBookingResult(TourBookingStatus.CONFIRMED.name))

        mockMvc.perform(post("/api/v1/bookings/$bookingUuid/confirm"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    fun confirm_returns404_whenTheBookingDoesNotExist() {
        whenever(confirmTourBookingUseCase.confirm(any()))
            .thenThrow(BookingNotFoundException(bookingUuid))

        mockMvc.perform(post("/api/v1/bookings/$bookingUuid/confirm"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun confirm_returns409_whenTheBookingIsNotRequested() {
        whenever(confirmTourBookingUseCase.confirm(any()))
            .thenThrow(InvalidBookingStateException(TourBookingStatus.ACTIVE))

        mockMvc.perform(post("/api/v1/bookings/$bookingUuid/confirm"))
            .andExpect(status().isConflict)
    }

    @Test
    fun confirm_returns400_whenTheIdentifierIsNotAUuid() {
        whenever(confirmTourBookingUseCase.confirm(any()))
            .thenThrow(IllegalArgumentException("Invalid UUID string: not-a-uuid"))

        mockMvc.perform(post("/api/v1/bookings/not-a-uuid/confirm"))
            .andExpect(status().isBadRequest)
    }
}
