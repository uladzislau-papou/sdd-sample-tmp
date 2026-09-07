package com.example.service.guide.inbound.rest

import com.example.service.guide.core.domain.guidetour.GuideTourStatus
import com.example.service.guide.core.domain.guidetour.exception.GuideTourNotFoundException
import com.example.service.guide.core.domain.guidetour.exception.InvalidGuideTourStateException
import com.example.service.guide.core.domain.guidetour.exception.TourStartTooEarlyException
import com.example.service.guide.core.inport.command.StartTourCommand
import com.example.service.guide.core.inport.result.StartTourResult
import com.example.service.guide.core.inport.usecase.StartTourUseCase
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
import java.time.Instant
import java.util.UUID

/**
 * Web slice tests for [GuideTourRestController].
 *
 * SDD: slice test per `documentation/test.definition.md` § 2.4, and
 * `documentation/use-cases/uc05-start-tour.spec.md` § 9.
 */
@WebMvcTest(controllers = [GuideTourRestController::class])
class GuideTourRestControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var startTourUseCase: StartTourUseCase

    private val guideTourId: String = UUID.randomUUID().toString()

    private fun start(body: String? = null) =
        mockMvc.perform(
            post("/api/v1/guide-tours/$guideTourId/start").apply {
                if (body != null) contentType(MediaType.APPLICATION_JSON).content(body)
            },
        )

    @Test
    fun start_returns200_withRunningStatus() {
        whenever(startTourUseCase.start(any())).thenReturn(StartTourResult(GuideTourStatus.RUNNING.name))

        start()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RUNNING"))
    }

    @Test
    fun start_sendsNoTimestamp_whenTheBodyIsAbsent() {
        whenever(startTourUseCase.start(any())).thenReturn(StartTourResult(GuideTourStatus.RUNNING.name))

        start()

        val captor = argumentCaptor<StartTourCommand>()
        verify(startTourUseCase).start(captor.capture())
        assertThat(captor.firstValue.startedAt).isNull()
    }

    @Test
    fun start_relaysTheTimestamp_whenTheBodyCarriesOne() {
        whenever(startTourUseCase.start(any())).thenReturn(StartTourResult(GuideTourStatus.RUNNING.name))

        start("""{ "startedAt": "2026-06-01T09:05:00Z" }""")

        val captor = argumentCaptor<StartTourCommand>()
        verify(startTourUseCase).start(captor.capture())
        assertThat(captor.firstValue.startedAt).isEqualTo(Instant.parse("2026-06-01T09:05:00Z"))
    }

    @Test
    fun start_returns404_whenTheTourDoesNotExist() {
        whenever(startTourUseCase.start(any())).thenThrow(GuideTourNotFoundException(guideTourId))

        start().andExpect(status().isNotFound)
    }

    @Test
    fun start_returns409_whenTheTourIsNotScheduled() {
        whenever(startTourUseCase.start(any()))
            .thenThrow(InvalidGuideTourStateException(GuideTourStatus.RUNNING))

        start().andExpect(status().isConflict)
    }

    @Test
    fun start_returns409_whenStartedBeforeTheScheduledTime() {
        whenever(startTourUseCase.start(any()))
            .thenThrow(
                TourStartTooEarlyException(
                    Instant.parse("2026-06-01T09:00:00Z"),
                    Instant.parse("2026-06-01T08:00:00Z"),
                ),
            )

        start().andExpect(status().isConflict)
    }

    @Test
    fun start_returns400_whenTheIdentifierIsNotAUuid() {
        whenever(startTourUseCase.start(any()))
            .thenThrow(IllegalArgumentException("Invalid UUID string: nope"))

        mockMvc.perform(post("/api/v1/guide-tours/nope/start")).andExpect(status().isBadRequest)
    }
}
