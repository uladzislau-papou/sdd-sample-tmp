package com.example.contractmanagement.booking.inbound.graphql

import java.time.LocalDate

/**
 * GraphQL input for UC01 — RequestTourBooking.
 *
 * Separate from the REST request type on purpose. They look alike today and will not stay
 * alike: GraphQL carries the date as a `String!` because the schema has no date scalar
 * declared, while REST binds a `LocalDate` directly. Sharing one type would force one
 * transport's representation onto the other.
 *
 * SDD: see `documentation/use-cases/uc01-request-tour-booking.spec.md` § 9.
 */
data class RequestTourBookingInput(
    val tourId: String,
    val tourDate: String,
    val participantCount: Int,
    val contactName: String,
    val contactEmail: String,
) {
    /** Parses [tourDate]; an unparseable value surfaces as a 400-equivalent GraphQL error. */
    fun tourDateAsLocalDate(): LocalDate = LocalDate.parse(tourDate)
}
