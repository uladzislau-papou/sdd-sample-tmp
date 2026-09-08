package com.example.contractmanagement.booking.inbound.graphql

import com.example.contractmanagement.booking.core.domain.tourbooking.TourBookingStatus
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.CapacityExceededException
import com.example.contractmanagement.booking.core.domain.tourbooking.exception.InvalidBookingRequestException
import com.example.contractmanagement.booking.core.inport.command.RequestTourBookingCommand
import com.example.contractmanagement.booking.core.inport.result.RequestTourBookingResult
import com.example.contractmanagement.booking.core.inport.usecase.RequestTourBookingUseCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.util.UUID

/**
 * GraphQL slice tests for [TourBookingGraphQLController].
 *
 * The point of this test is not GraphQL coverage for its own sake. UC01 is reachable over
 * two transports, and these tests assert that the **same inbound port** receives an
 * equivalent command from the GraphQL adapter as from the REST one — which is the claim
 * Ports & Adapters makes and the thing that silently stops being true first.
 *
 * SDD: slice test per `documentation/test.definition.md` § 2.4.
 */
@GraphQlTest(controllers = [TourBookingGraphQLController::class])
class TourBookingGraphQLControllerTest {
    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @MockitoBean
    private lateinit var requestTourBookingUseCase: RequestTourBookingUseCase

    private val bookingUuid: String = UUID.randomUUID().toString()

    private val mutation =
        """
        mutation {
          requestTourBooking(input: {
            tourId: "TOUR-42"
            tourDate: "2026-06-15"
            participantCount: 3
            contactName: "Alice"
            contactEmail: "alice@example.com"
          }) {
            bookingId
            status
          }
        }
        """.trimIndent()

    @Test
    fun requestTourBooking_returnsBookingIdAndStatus() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenReturn(RequestTourBookingResult(bookingUuid, TourBookingStatus.REQUESTED.name))

        graphQlTester
            .document(mutation)
            .execute()
            .path("requestTourBooking.bookingId")
            .entity(String::class.java)
            .isEqualTo(bookingUuid)
    }

    @Test
    fun requestTourBooking_buildsTheSameCommandAsTheRestAdapter() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenReturn(RequestTourBookingResult(bookingUuid, TourBookingStatus.REQUESTED.name))

        graphQlTester.document(mutation).execute()

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
    fun requestTourBooking_reportsBadRequest_whenTheDomainRejectsTheRequest() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenThrow(InvalidBookingRequestException("Tour date must be in the future"))

        graphQlTester
            .document(mutation)
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors.first().errorType).isEqualTo(ErrorType.BAD_REQUEST)
                assertThat(errors.first().message).isEqualTo("Tour date must be in the future")
            }
    }

    @Test
    fun requestTourBooking_reportsBadRequest_whenCapacityIsExceeded() {
        whenever(requestTourBookingUseCase.request(any()))
            .thenThrow(CapacityExceededException(5, 2))

        graphQlTester
            .document(mutation)
            .execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors.first().errorType).isEqualTo(ErrorType.BAD_REQUEST)
            }
    }
}
